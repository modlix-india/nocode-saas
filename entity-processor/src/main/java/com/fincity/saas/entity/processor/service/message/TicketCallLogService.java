package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.CallDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.message.Call;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.message.CallEventRequest;
import com.fincity.saas.entity.processor.oserver.message.enums.call.CallStatus;
import com.fincity.saas.entity.processor.oserver.message.enums.call.ExotelCallStatus;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Calls, gated on deal access.
 *
 * <p>The reason this exists is the same as for WhatsApp conversations: the message service cannot
 * answer "may this user see this call". Until now nothing did. {@code POST
 * /api/message/call/exotel/eager/query} filtered on a customer phone number returned any customer's
 * call history, recording URLs included, to any authenticated user in the tenant, and {@code
 * /api/message/call/make} let any of them dial any number on the tenant's Exotel account. The deal
 * profile page was only incidentally safe because a user got there from a deal they could open.
 *
 * <p>Both directions write their row here at the moment they happen, which is what keeps the deal
 * link exact. An outbound call knows its deal because the caller named it and it was checked; an
 * inbound one knows its deal because {@code TicketCallService} has already resolved or created it
 * before connecting. Provider status callbacks then merge onto that row by the provider's call id,
 * so nothing has to re-derive the deal from a phone number later.
 */
@Service
public class TicketCallLogService {

    private static final Logger logger = LoggerFactory.getLogger(TicketCallLogService.class);

    private static final String EXOTEL_PROVIDER = "EXOTEL";
    private static final String DIRECTION_OUTBOUND = "outbound-api";
    private static final String DIRECTION_INBOUND = "inbound";

    private final TicketService ticketService;
    private final CallDAO callDAO;
    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;
    private final ObjectMapper objectMapper;

    public TicketCallLogService(
            TicketService ticketService,
            CallDAO callDAO,
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.callDAO = callDAO;
        this.feignMessageService = feignMessageService;
        this.msgService = msgService;
        this.objectMapper = objectMapper;
    }

    /**
     * A deal's call log.
     *
     * <p>The gate is {@code readByIdentity(access, ...)}, the same condition every other deal read
     * uses: assigned user within the caller's reporting tree, business-partner client scoping and
     * per-product read rules. A deal the caller cannot see fails there, before any call is read.
     *
     * <p>No separate call authority, matching how conversations, notes and tasks work in this app:
     * roles scope entities, not the tabs within a deal.
     */
    public Mono<Page<Map<String, Object>>> readTicketCalls(Identity ticketId, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        this::visibleDealsOnSameNumber,
                        (access, ticket, ticketIds) -> this.callDAO.readCallsEager(
                                access.getAppCode(), access.getClientCode(), ticketIds, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallLogService.readTicketCalls"));
    }

    /**
     * Every deal on this customer's number that the caller can see, including the one they opened.
     *
     * <p>Same union as the WhatsApp thread, and for the same reason: a customer holding several
     * deals made one set of phone calls, so showing a fragment of it per deal would misrepresent
     * what happened. Falls back to the opened deal alone when it has no number to union over.
     */
    private Mono<List<ULong>> visibleDealsOnSameNumber(ProcessorAccess access, Ticket ticket) {

        if (ticket.getPhoneNumber() == null || ticket.getPhoneNumber().isBlank())
            return Mono.just(List.of(ticket.getId()));

        return this.ticketService
                .readAccessibleTicketIdsByPhone(access, ticket.getPhoneNumber(), ticket.getProductId())
                .map(ids -> ids.isEmpty() ? List.of(ticket.getId()) : ids);
    }

    /**
     * Places a call to a deal's customer.
     *
     * <p>The number dialled comes from the deal, never from the request. That is the substantive
     * change from the endpoint this replaces: the page happened to send the deal's own number, but
     * nothing enforced it, so a caller could pair a deal they could see with any number they liked
     * and place a call on the tenant's account against it. Taking the number server-side means the
     * deal check actually constrains who can be rung.
     */
    public Mono<Call> makeCall(Identity ticketId, String connectionName, String callerId) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (ProcessorAccess access, Ticket ticket) -> this.place(access, ticket, connectionName, callerId),
                        (ProcessorAccess access, Ticket ticket, Map<String, Object> placed) ->
                                this.recordPlacedCall(access, ticket, connectionName, placed))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallLogService.makeCall"));
    }

    private Mono<Map<String, Object>> place(
            ProcessorAccess access, Ticket ticket, String connectionName, String callerId) {

        if (ticket.getPhoneNumber() == null || ticket.getPhoneNumber().isBlank())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "phoneNumber");

        PhoneNumber to = PhoneNumber.of(ticket.getDialCode(), ticket.getPhoneNumber());

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("toNumber", Map.of("countryCode", to.getCountryCode(), "number", to.getNumber()));
        if (connectionName != null && !connectionName.isBlank()) request.put("connectionName", connectionName);
        if (callerId != null && !callerId.isBlank()) request.put("callerId", callerId);

        return this.feignMessageService.makeCallInternal(access.getAppCode(), access.getClientCode(), request);
    }

    /**
     * Writes the row for a call we just placed, with the deal already known.
     *
     * <p>Done here rather than left to the first status callback so the deal link never has to be
     * guessed from a phone number, and so the call appears in the log immediately instead of when
     * the provider gets round to calling back.
     */
    private Mono<Call> recordPlacedCall(
            ProcessorAccess access, Ticket ticket, String connectionName, Map<String, Object> placed) {

        Call call = this.fromProviderMap(placed)
                .setTicketId(ticket.getId())
                .setProductId(ticket.getProductId())
                .setConnectionName(connectionName)
                .setCallProvider(EXOTEL_PROVIDER)
                .setOutbound(true);

        if (call.getDirection() == null) call.setDirection(DIRECTION_OUTBOUND);

        return this.upsert(access.getAppCode(), access.getClientCode(), call);
    }

    /**
     * Records a call this service connected but did not place.
     *
     * <p>Called from the inbound connect-applet flow, which has already resolved or created the deal
     * in order to know whom to ring, so the link is exact rather than matched later.
     */
    public Mono<Call> recordIncomingCall(
            ProcessorAccess access,
            Ticket ticket,
            String providerCallId,
            String connectionName,
            PhoneNumber from,
            PhoneNumber callerId,
            Map<String, Object> appletRequest) {

        Call call = new Call()
                .setProviderCallId(providerCallId)
                .setTicketId(ticket.getId())
                .setProductId(ticket.getProductId())
                .setConnectionName(connectionName)
                .setCallProvider(EXOTEL_PROVIDER)
                .setOutbound(false)
                .setDirection(DIRECTION_INBOUND)
                .setCallStatus(CallStatus.ORIGINATE)
                .setExotelCallStatus(ExotelCallStatus.IN_PROGRESS)
                .setExotelConnectAppletRequest(appletRequest);

        if (from != null) {
            call.setFromDialCode(from.getCountryCode())
                    .setFrom(from.getNumber())
                    .setCustomerDialCode(from.getCountryCode())
                    .setCustomerPhoneNumber(from.getNumber());
        }

        if (callerId != null) call.setCallerId(callerId.getNumber());

        return this.upsert(access.getAppCode(), access.getClientCode(), call)
                // An inbound call that connects but whose log row fails to write is still a
                // connected call. Failing the applet response here would drop a live customer.
                .onErrorResume(e -> {
                    logger.error(
                            "Could not record the inbound call {} against deal {}. The call itself connected.",
                            providerCallId,
                            ticket.getId(),
                            e);
                    return Mono.empty();
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallLogService.recordIncomingCall"));
    }

    /**
     * Accepts a provider event handed over by the message service.
     *
     * <p>Almost always a status update for a row that already exists, which is merged rather than
     * replaced: provider callbacks are partial, so a late one carrying only a status must not blank
     * the numbers an earlier event recorded.
     *
     * <p>An event for a call this service has never seen still gets a row, with no deal attached.
     * That is deliberate: a call placed outside the gated endpoint is exactly the thing worth having
     * a record of, and losing it because it does not fit the expected flow would be the wrong
     * trade.
     */
    public Mono<Call> accept(String appCode, String clientCode, CallEventRequest request) {

        if (request == null || request.getProviderCallId() == null
                || request.getProviderCallId().isBlank())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "providerCallId");

        return this.upsert(appCode, clientCode, this.fromEvent(request))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketCallLogService.accept"));
    }

    /**
     * Creates or merges by the provider's call id.
     *
     * <p>The unique key on (app, client, provider call id) is what makes this safe under retry: a
     * duplicate callback, an outbox replay after a failed delete, and a status arriving before the
     * call row all converge on the same row instead of multiplying it.
     */
    private Mono<Call> upsert(String appCode, String clientCode, Call incoming) {

        return this.callDAO
                .readByProviderCallId(appCode, clientCode, incoming.getProviderCallId())
                .flatMap(existing -> this.callDAO.update(existing.merge(incoming)))
                .switchIfEmpty(Mono.defer(() -> {
                    incoming.setAppCode(appCode);
                    incoming.setClientCode(clientCode);
                    return this.callDAO.create(incoming);
                }));
    }

    /** Maps a provider event onto the stored shape, leaving anything it did not report null. */
    private Call fromEvent(CallEventRequest request) {

        ExotelCallStatus providerStatus = ExotelCallStatus.of(request.getCallStatus());

        Call call = new Call()
                .setProviderCallId(request.getProviderCallId())
                .setParentCallSid(request.getParentCallSid())
                .setAccountSid(request.getAccountSid())
                .setProductId(ULongUtil.valueOf(request.getProductId()))
                .setConnectionName(request.getConnectionName())
                .setCallProvider(request.getCallProvider() == null ? EXOTEL_PROVIDER : request.getCallProvider())
                .setFromDialCode(request.getFromDialCode())
                .setFrom(request.getFrom())
                .setToDialCode(request.getToDialCode())
                .setTo(request.getTo())
                .setCustomerDialCode(request.getCustomerDialCode())
                .setCustomerPhoneNumber(request.getCustomerPhoneNumber())
                .setCallerId(request.getCallerId())
                .setDirection(request.getDirection())
                .setAnsweredBy(request.getAnsweredBy())
                .setStartTime(request.getStartTime())
                .setEndTime(request.getEndTime())
                .setDuration(request.getDuration())
                .setConversationDuration(request.getConversationDuration())
                .setRecordingUrl(request.getRecordingUrl())
                .setLeg1Status(ExotelCallStatus.of(request.getLeg1Status()))
                .setLeg2Status(ExotelCallStatus.of(request.getLeg2Status()))
                .setExotelCallRequest(request.getExotelCallRequest())
                .setExotelConnectAppletRequest(request.getExotelConnectAppletRequest())
                .setExotelCallResponse(request.getExotelCallResponse());

        if (request.getOutbound() != null) call.setOutbound(request.getOutbound());

        if (providerStatus != null) {
            call.setExotelCallStatus(providerStatus).setCallStatus(providerStatus.toCallStatus());
        }

        call.setPrice(parsePrice(request.getPrice()));

        return call;
    }

    /** Maps the message service's own call representation, returned when we place a call. */
    private Call fromProviderMap(Map<String, Object> placed) {

        if (placed == null || placed.isEmpty()) return new Call();

        CallEventRequest asEvent = this.objectMapper.convertValue(placed, CallEventRequest.class);

        // The message service calls it `sid`; everything on this side calls it the provider call id.
        if (asEvent.getProviderCallId() == null) asEvent.setProviderCallId(asString(placed.get("sid")));

        if (asEvent.getCallStatus() == null) asEvent.setCallStatus(asString(placed.get("exotelCallStatus")));

        return this.fromEvent(asEvent);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * The provider reports price as a string and occasionally as an empty one. A malformed price is
     * not worth failing a call record over.
     */
    private static BigDecimal parsePrice(String price) {
        if (price == null || price.isBlank()) return null;
        try {
            return new BigDecimal(price.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Exposed for the conversation list, so a deal that has been rung can be distinguished. */
    public Mono<List<ULong>> ticketsWithCalls(String appCode, String clientCode, List<ULong> ticketIds) {
        return this.callDAO.ticketsWithCalls(appCode, clientCode, ticketIds);
    }

    /** Reads a provider payload back as a map, for callers that only hold the serialized form. */
    public Map<String, Object> asMap(Object payload) {
        return this.objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }
}
