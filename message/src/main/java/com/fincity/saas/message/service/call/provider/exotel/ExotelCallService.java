package com.fincity.saas.message.service.call.provider.exotel;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelDirection;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelErrorResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelOutboundCallResult;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.IBrowserCallService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO>
        implements IBrowserCallService {

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    private EventDispatcher eventDispatcher;

    /**
     * Who owns a call this service did not have an explicit owner for.
     *
     * <p>Configured rather than derived because, unlike WhatsApp, Exotel numbers are held in the
     * CRM's product configuration and this service has no table of them to read an owner from. One
     * consumer exists, so a default is honest; the moment there are two, calls need their own number
     * table and this should go.
     */
    @Value("${message.call.default-owner-service:entity-processor}")
    private String defaultCallOwnerService;

    @Autowired
    public void setEventDispatcher(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    private ExotelIntegrationsService integrationsService;

    @Autowired
    public void setIntegrationsService(ExotelIntegrationsService integrationsService) {
        this.integrationsService = integrationsService;
    }

    private ProviderUserEndpointDAO providerUserEndpointDAO;

    @Autowired
    public void setProviderUserEndpointDAO(ProviderUserEndpointDAO providerUserEndpointDAO) {
        this.providerUserEndpointDAO = providerUserEndpointDAO;
    }

    // -----------------------------------------------------------------------------------------
    // IBrowserCallService — delegated to ExotelIntegrationsService.
    //
    // Implemented here rather than on that service so the capability is discovered from the same
    // object CallService already registers: the dispatcher derives its browser map by testing the
    // registered provider with instanceof, which means a provider cannot be half-registered.
    // The Integrations Core client stays in its own class, which is worth it given its size.
    // -----------------------------------------------------------------------------------------

    @Override
    public Mono<CallAppStatus> initializeApp(MessageAccess access, Connection connection) {
        return this.integrationsService.initializeApp(access, connection);
    }

    @Override
    public Mono<ProvisionedAgent> provisionAgent(
            MessageAccess access, Connection connection, ProvisionAgentRequest request) {
        return this.integrationsService.provisionAgent(access, connection, request);
    }

    @Override
    public Mono<Boolean> teardownApp(MessageAccess access, Connection connection) {
        return this.integrationsService.teardownApp(access, connection);
    }

    @Override
    public Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection) {
        return this.integrationsService.getAgentEndpoints(access, connection);
    }

    @Override
    public Mono<CallAppStatus> callAppStatus(MessageAccess access) {
        return this.integrationsService.callAppStatus(access);
    }

    @Override
    public Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId) {
        return this.integrationsService.deactivateAgent(access, connection, userId);
    }

    @Override
    public Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId) {
        return this.integrationsService.generateBrowserToken(access, connection, userId);
    }

    @Override
    public Mono<BrowserCallStatus> browserCallStatus(
            MessageAccess access, Connection connection, ULong userId, boolean verifyWithProvider) {
        return this.integrationsService.browserCallStatus(access, connection, userId, verifyWithProvider);
    }

    /**
     * Places a browser call for a service that has already checked the deal, and returns the
     * provider-shaped call.
     *
     * <p>Provider-shaped for the same reason {@link #makeCallInternal} is: the caller needs the
     * {@code Sid} to key its own record on, and the neutral {@link Call} has nowhere to carry it —
     * it models the connection, the provider and the direction, not the provider's own identifiers.
     * Returning the neutral form here left every row on the calling side with a null provider call
     * id, so the callback that arrived minutes later matched nothing and recorded the call a second
     * time: once against the deal with no outcome, once with the outcome and no deal.
     *
     * <p>Resolves the connection itself, again like {@code makeCallInternal}, so the route above it
     * stays a thin pass-through.
     */
    public Mono<ExotelCall> browserDialInternal(
            String appCode, String clientCode, String connectionName, ULong userId, String toNumber) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(appCode, clientCode, connectionName),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) ->
                                this.integrationsService.placeOutboundCall(access, connection, userId, toNumber),
                        (connection, vConn, result) ->
                                this.createInternal(access, userId, this.fromDialResult(connection, toNumber, result)),
                        (connection, vConn, result, created) -> this.toCall(created)
                                .map(call -> call.setConnectionName(connection.getName()))
                                .flatMap(call -> super.callService.createInternal(access, userId, call))
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.browserDialInternal"));
    }

    /**
     * Builds the row from what the provider reported.
     *
     * <p>Prefers the provider's own view of the call over ours wherever it has one: the virtual
     * number it actually presented as the caller id, and the SIP endpoint it actually originated
     * from. Those can differ from what our endpoint rows say — a stale mapping, or an agent
     * re-provisioned since — and when they do, the provider is right about what happened.
     */
    private ExotelCall fromDialResult(Connection connection, String toNumber, ExotelOutboundCallResult result) {

        PhoneNumber to = PhoneUtil.parse(toNumber);

        ExotelCall call = new ExotelCall()
                .setSid(result.getCallSid())
                .setDirection(ExotelDirection.OUTBOUND_API.name())
                .setToDialCode(to == null ? null : to.getCountryCode())
                .setTo(to == null ? toNumber : to.getNumber())
                .setCustomerDialCode(to == null ? null : to.getCountryCode())
                .setCustomerPhoneNumber(to == null ? toNumber : to.getNumber())
                .setAccountSid((String) connection.getConnectionDetails().getOrDefault("accountSid", ""))
                .setStartTime(LocalDateTime.now())
                .setOwnerService(this.defaultCallOwnerService);

        if (result.getVirtualNumber() != null && !result.getVirtualNumber().isBlank())
            call.setCallerId(result.getVirtualNumber());

        // The agent's SIP endpoint, which for a browser call is genuinely where the call came from.
        if (result.getFromNumber() != null && !result.getFromNumber().isBlank()) call.setFrom(result.getFromNumber());

        // "active" the moment the provider has dispatched the invite. Anything else it reports is
        // taken as-is; a blank status leaves the column null for the first callback to fill.
        ExotelCallStatus reported = ExotelCallStatus.lookupLiteral(result.getCallState());
        if (reported != null) call.setExotelCallStatus(reported);

        return call;
    }

    /**
     * Finds the call a callback belongs to.
     *
     * <p>On the provider's call id, which every callback carries and which is recorded on the row the
     * moment the call is placed — the dial response supplies it synchronously, so there is never a
     * window where a call of ours exists without one.
     *
     * <p>Returning empty is a real outcome, not a failure. An agent's browser holds a token that can
     * reach the provider's dial API directly, so calls get placed that this service never recorded
     * and never will. Failing the callback would only make the provider retry something that cannot
     * succeed, and would lose the events for every call that <i>is</i> ours in the same batch. These
     * services do not log, so an unattributed call leaves no trace at all — reconciling them means
     * comparing our rows against the provider's call list, not reading a log.
     *
     * <p><b>Deliberately not scoped to a tenant</b>, and it takes no {@code MessageAccess} for that
     * reason. The scoped overload exists on {@code BaseProviderDAO}, but the tenant a callback URL
     * resolves to is not always the tenant that owns the call: webhook URLs commonly resolve to
     * {@code SYSTEM} while the call belongs to a sub-client, so scoping here drops legitimate
     * callbacks. The row found supplies the access the follow-up write uses, so the update still
     * lands under the owning tenant.
     *
     * <p>The cost is real and recorded rather than hidden: both callback routes are {@code
     * permitAll}, so anyone holding a {@code CallSid} — which is embedded in every recording URL the
     * provider issues — can drive an update against that call's row whatever headers they send.
     * Scoping is the wrong layer for that; the endpoint needs authenticating, which is tracked as a
     * deliberate deferral rather than an oversight.
     */
    private Mono<ExotelCall> resolveCallbackTarget(String callSid) {

        if (StringUtil.safeIsBlank(callSid)) return Mono.empty();

        return super.dao.findByUniqueField(callSid);
    }

    /**
     * Hands a call update to the service that owns the call.
     *
     * <p>Through the outbox, so Exotel gets its 200 as soon as the row is durable rather than after
     * the consumer has accepted it. A consumer outage therefore delays a call log, it does not lose
     * one and it does not make us look unavailable to Exotel.
     *
     * <p><b>A failed enqueue does fail the callback, deliberately.</b> Delivery failures are
     * already swallowed inside {@code enqueueAndDispatch} — the row is durable, so the sweeper
     * retries and the webhook must not be told — which leaves the outbox write itself as the only
     * failure that can surface here. That one has no sweeper to recover it: there is no row to
     * retry, so the event is simply gone.
     *
     * <p>Propagating turns that into a retry. The provider re-sends the callback, the merge is keyed
     * on the call id and idempotent, and a transient write can succeed the second time. Swallowing
     * it instead recorded nothing anywhere and lost the status permanently — these services do not
     * log, so it left no trace either.
     */
    private Mono<Void> handOverToOwner(ExotelCall call) {

        CallEventDispatch dispatch = new CallEventDispatch()
                .setProviderCallId(call.getSid())
                .setParentCallSid(call.getParentCallSid())
                .setAccountSid(call.getAccountSid())
                .setEventType(DispatchEventType.CALL_STATUS.name())
                .setCallProvider(this.getConnectionSubType().getProvider())
                .setOutbound(ExotelDirection.getByName(call.getDirection()).isOutbound())
                .setFromDialCode(call.getFromDialCode())
                .setFrom(call.getFrom())
                .setToDialCode(call.getToDialCode())
                .setTo(call.getTo())
                .setCustomerDialCode(call.getCustomerDialCode())
                .setCustomerPhoneNumber(call.getCustomerPhoneNumber())
                .setCallerId(call.getCallerId())
                .setCallStatus(
                        call.getExotelCallStatus() == null
                                ? null
                                : call.getExotelCallStatus().getDisplayName())
                .setLeg1Status(
                        call.getLeg1Status() == null
                                ? null
                                : call.getLeg1Status().getDisplayName())
                .setLeg2Status(
                        call.getLeg2Status() == null
                                ? null
                                : call.getLeg2Status().getDisplayName())
                .setDirection(call.getDirection())
                .setAnsweredBy(call.getAnsweredBy())
                .setStartTime(call.getStartTime())
                .setEndTime(call.getEndTime())
                .setDuration(call.getDuration())
                .setConversationDuration(call.getConversationDuration())
                .setPrice(call.getPrice() == null ? null : call.getPrice().toString())
                .setRecordingUrl(call.getRecordingUrl());

        String owner = call.getOwnerService() == null ? this.defaultCallOwnerService : call.getOwnerService();

        return this.eventDispatcher.enqueueAndDispatch(
                MessageAccess.of(call.getAppCode(), call.getClientCode(), Boolean.TRUE),
                owner,
                DispatchEventType.CALL_STATUS,
                call.getSid(),
                dispatch);
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.EXOTEL_CALL;
    }

    @Override
    protected String getCacheName() {
        return EXOTEL_CALL_CACHE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.EXOTEL;
    }

    @Override
    public String getProviderUri() {
        return EXOTEL_PROVIDER_URI;
    }

    @Override
    protected Mono<ExotelCall> updatableEntity(ExotelCall entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setParentCallSid(entity.getParentCallSid());
            existing.setDateCreated(entity.getDateCreated());
            existing.setDateUpdated(entity.getDateUpdated());

            existing.setExotelCallStatus(entity.getExotelCallStatus());
            existing.setEndTime(entity.getEndTime());
            existing.setDuration(entity.getDuration());
            existing.setPrice(entity.getPrice());
            existing.setDirection(entity.getDirection());
            existing.setAnsweredBy(entity.getAnsweredBy());
            existing.setRecordingUrl(entity.getRecordingUrl());
            existing.setConversationDuration(entity.getConversationDuration());
            existing.setLeg1Status(entity.getLeg1Status());
            existing.setLeg2Status(entity.getLeg2Status());
            existing.setLegs(entity.getLegs());
            existing.setExotelCallResponse(entity.getExotelCallResponse());

            return Mono.just(existing);
        });
    }

    @Override
    public Mono<Call> toCall(ExotelCall providerObject) {
        return Mono.just(new Call()
                        .setUserId(providerObject.getUserId())
                        .setCallProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(ExotelDirection.getByName(providerObject.getDirection())
                                .isOutbound())
                        .setExotelCallId(providerObject.getId() != null ? providerObject.getId() : null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.toCall"));
    }

    @Override
    public Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {
        String to = callRequest.getToNumber().getNumber();

        String callerId = callRequest.getCallerId() == null
                ? (String) connection.getConnectionDetails().get(ExotelCallRequest.Fields.callerId)
                : callRequest.getCallerId().getLandlineNumber();

        if (to == null) return super.throwMissingParam(ExotelCallRequest.Fields.to);

        if (callerId == null) return super.throwMissingParam(ExotelCallRequest.Fields.callerId);

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.makeExotelCall(access, exotelCallRequest, connection),
                        (vConn, eCreated) ->
                                this.toCall(eCreated).map(call -> call.setConnectionName(connection.getName())),
                        (vConn, eCreated, call) -> super.callService.createInternal(access, call),
                        (vConn, eCreated, call, cCall) -> super.callEventService
                                .sendMakeCallEvent(
                                        access.getAppCode(), access.getClientCode(), access.getUserId(), eCreated)
                                .thenReturn(cCall))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall"));
    }

    /**
     * Places a call on behalf of another service, and records who to route its callbacks to.
     *
     * <p>Returns the provider-shaped call rather than the thin {@code Call} wrapper, because the
     * caller needs the provider's Sid to key its own record on and nothing else here can give it to
     * them.
     *
     * <p>No deal check, and none is possible: this service cannot evaluate one. The caller has
     * already done it, and the number dialled is theirs to justify. That is exactly why the public
     * {@code /make} endpoint should not be reachable from a browser.
     */
    public Mono<ExotelCall> makeCallInternal(
            String appCode, String clientCode, CallRequest callRequest, String ownerService) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(
                                appCode, clientCode, callRequest.getConnectionName()),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) ->
                                this.makeExotelCall(access, this.toExotelRequest(callRequest, connection), connection),
                        (connection, vConn, eCreated) ->
                                super.updateInternalWithoutUser(access, eCreated.setOwnerService(ownerService)),
                        (connection, vConn, eCreated, stamped) -> this.toCall(stamped)
                                .map(call -> call.setConnectionName(connection.getName()))
                                .flatMap(call -> super.callService.createInternal(access, call))
                                .thenReturn(stamped))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCallInternal"));
    }

    private ExotelCallRequest toExotelRequest(CallRequest callRequest, Connection connection) {

        String to = callRequest.getToNumber().getNumber();
        String callerId = callRequest.getCallerId() == null
                ? (String) connection.getConnectionDetails().get(ExotelCallRequest.Fields.callerId)
                : callRequest.getCallerId().getLandlineNumber();

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());
        return exotelCallRequest;
    }

    public Mono<Call> makeCall(CallRequest callRequest) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.callConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), callRequest.getConnectionName()),
                        (access, connection) -> super.isValidConnection(connection),
                        (access, connection, vConn) -> this.makeCall(access, callRequest, connection))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall(CallRequest)"));
    }

    private void applyConnectionDetailsToRequest(ExotelCallRequest request, Map<String, Object> details) {
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.callType, String.class),
                request::setCallType);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.timeLimit, Integer.class),
                request::setTimeLimit);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.timeOut, Integer.class),
                request::setTimeOut);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.waitUrl, String.class),
                request::setWaitUrl);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.doRecord, Boolean.class),
                request::setDoRecord);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingChannels, String.class),
                request::setRecordingChannels);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingFormat, String.class),
                request::setRecordingFormat);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallback, String.class),
                request::setStatusCallback);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackEvents, String[].class),
                request::setStatusCallbackEvents);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackContentType, String.class),
                request::setStatusCallbackContentType);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.customField, String.class),
                request::setCustomField);
    }

    private Mono<ExotelCall> makeExotelCall(MessageAccess messageAccess, ExotelCallRequest request, Connection conn) {

        request.setFrom(
                PhoneUtil.parse(messageAccess.getUser().getPhoneNumber()).getNumber());

        return FlatMapUtil.flatMapMono(
                        () -> super.getCallBackAppUrl(conn.getAppCode()),
                        callBackUri -> request.setStatusCallback(callBackUri).toFormDataAsync(),
                        (callBackUri, formData) -> webClientConfig.createExotelWebClient(conn),
                        (callBackUri, formData, webClient) -> {
                            return webClient
                                    .post()
                                    .uri(ExotelApiConfig.getCallUrl())
                                    .contentType(MediaType.MULTIPART_FORM_DATA)
                                    .bodyValue(formData)
                                    .retrieve()
                                    .onStatus(
                                            status -> status.is4xxClientError() || status.is5xxServerError(),
                                            clientResponse -> clientResponse
                                                    .bodyToMono(ExotelErrorResponse.class)
                                                    .flatMap(errorBody -> {
                                                        return this.msgService.throwStrMessage(
                                                                msg -> new GenericException(
                                                                        HttpStatus.resolve(
                                                                                errorBody
                                                                                        .getRestException()
                                                                                        .getStatus()),
                                                                        msg),
                                                                errorBody
                                                                        .getRestException()
                                                                        .getMessage());
                                                    }))
                                    .bodyToMono(ExotelCallResponse.class);
                        },
                        (callBackUri, formData, webClient, response) -> this.createInternal(
                                messageAccess, ExotelCall.ofOutbound(request).update(response)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeExotelCall"));
    }

    public Mono<ExotelConnectAppletResponse> connectCall(
            String appCode, String clientCode, IncomingCallRequest request) {

        if (request.getUserId() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "userId");

        Map<String, String> providerRequest = request.getProviderIncomingRequest();

        if (providerRequest == null || providerRequest.isEmpty())
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "providerIncomingRequest");

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(providerRequest);

        if (exotelRequest.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), request.getConnectionName()),
                        connection -> super.getUserIdAndPhone(request.getUserId()),
                        // A repeated CallSid answers with the destination again rather than erroring.
                        // The applet is retried — by the provider on a timeout, and by the flow itself
                        // when fetchAfterAttempt is set — and a second attempt failing would drop a
                        // live call that the first attempt had already recorded. The row is what makes
                        // this safe to repeat: it exists, so nothing is written twice.
                        (connection, user) -> this.existsByUniqueField(access, exotelRequest.getCallSid())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists))
                                        return this.createResponse(
                                                access,
                                                user.getId(),
                                                connection,
                                                user.getValue().getNumber());

                                    ExotelCall exotelCall = ExotelCall.ofInbound(
                                                    exotelRequest, user.getValue(), (String) connection
                                                            .getConnectionDetails()
                                                            .getOrDefault("accountSid", ""))
                                            // The connect applet is always answered by the owning
                                            // service, which is what reached us here, so the owner is
                                            // known at creation time and a later status callback never
                                            // has to guess.
                                            .setOwnerService(this.defaultCallOwnerService);

                                    Mono<ExotelCall> exotelCreated =
                                            this.createInternal(access, user.getId(), exotelCall);

                                    Mono<Call> callCreated = this.toCall(exotelCall)
                                            .map(call -> call.setConnectionName(connection.getName()))
                                            .flatMap(call ->
                                                    super.callService.createInternal(access, user.getId(), call));

                                    Mono<ExotelConnectAppletResponse> responseCreated = this.createResponse(
                                            access,
                                            user.getId(),
                                            connection,
                                            user.getValue().getNumber());

                                    return Mono.zip(exotelCreated, callCreated, responseCreated)
                                            .<ExotelConnectAppletResponse>flatMap(tuple -> super.callEventService
                                                    .sendIncomingCallEvent(
                                                            access.getAppCode(),
                                                            access.getClientCode(),
                                                            user.getId(),
                                                            tuple.getT1())
                                                    .thenReturn(tuple.getT3()));
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.connectCall"));
    }

    private Mono<ExotelConnectAppletResponse> createResponse(
            MessageAccess access, ULong userId, Connection connection, String fallbackPhone) {

        ExotelConnectAppletResponse response = new ExotelConnectAppletResponse();
        this.applyConnectionDetailsToResponse(response, connection.getConnectionDetails());

        // Every active endpoint for this agent, in PRIORITY order, exactly as stored. The DAO
        // already sorts by PRIORITY ascending, and that column is the reason the table exists: with
        // sequential ringing it is the order the provider dials, so the browser rings first and the
        // desk phone only after maxRingingDuration. Returning the rows in their stored order IS the
        // routing rule — rebuilding that order here would silently ignore a third endpoint type or a
        // deliberate re-prioritisation.
        //
        // The profile number is the fallback for having no rows at all, not an entry to append. An
        // agent provisioned for browser calling already holds a PSTN_PHONE row; preferring the
        // profile number over it means the call rings whichever of the two happened to be picked
        // when they disagree.
        return this.providerUserEndpointDAO
                .findActiveEndpoints(
                        access.getAppCode(),
                        userId,
                        connection.getName(),
                        this.getConnectionSubType().getProvider())
                .map(ProviderUserEndpoint::getEndpointValue)
                .filter(value -> value != null && !value.isBlank())
                .collectList()
                .map(numbers -> response.setDestination(new ExotelConnectAppletResponse.Destination()
                        .setNumbers(destinationNumbers(numbers, fallbackPhone))));
    }

    /**
     * The destinations the applet dials, given what is stored for the agent.
     *
     * <p>Extracted and pure so the routing rule can be tested without a database, which matters
     * because this changes inbound behaviour for <b>every</b> existing tenant — including those that
     * will never use browser calling. A tenant with only a {@code PSTN_PHONE} row must still ring
     * that row and nothing else.
     *
     * <p>Stored order is returned untouched. {@code PRIORITY} is why the table exists: ringing is
     * sequential, so the stored order <em>is</em> the routing rule, and rebuilding it here would
     * silently ignore a third endpoint type or a deliberate re-prioritisation.
     *
     * <p><b>The profile number is a fallback for having no rows, not an entry to append</b>, and
     * that is a product decision worth stating rather than inferring. Appending it would double-ring
     * whenever it disagrees with the stored {@code PSTN_PHONE} value. The cost is that an agent
     * whose PSTN row was deactivated, or who somehow holds only a SIP row, is unreachable when their
     * browser is shut — where previously the profile number always rang. Reachability now follows
     * what was provisioned, not what a profile happens to say.
     */
    static List<String> destinationNumbers(List<String> storedEndpoints, String fallbackPhone) {
        return storedEndpoints.isEmpty() ? fallbackOnly(fallbackPhone) : storedEndpoints;
    }

    /**
     * The agent's own number, for an agent with no provisioned endpoints at all.
     *
     * <p>Preserves the behaviour inbound calling had before browser calling existed: an agent who
     * has never been provisioned still rings on their phone rather than not at all. An empty list is
     * returned when even that is unknown, which the provider treats as nowhere to send the call —
     * correct, and better than inventing a destination.
     */
    private static List<String> fallbackOnly(String fallbackPhone) {
        return fallbackPhone == null || fallbackPhone.isBlank() ? List.of() : List.of(fallbackPhone);
    }

    private void applyConnectionDetailsToResponse(ExotelConnectAppletResponse response, Map<String, Object> details) {
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.fetchAfterAttempt, Boolean.class),
                response::setFetchAfterAttempt);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.doRecord, Boolean.class),
                response::setDoRecord);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.maxRingingDuration, Long.class),
                response::setMaxRingingDuration);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.Fields.maxConversationDuration, Long.class),
                response::setMaxConversationDuration);

        // Optional, and unset unless an operator configures it. Inbound status and recordings
        // arrive today through the App Bazaar Passthru applet configured in the provider's console
        // — verified on live inbound calls, which do report duration, talk time and a recording —
        // so this is not a gap in reporting. What it removes is the manual console step: set this
        // and the applet is told where to post, rather than depending on a dashboard entry that has
        // to be updated by hand whenever the callback host changes.
        //
        // Left absent by default on purpose. A tenant that already has the console entry would
        // otherwise receive the same event twice, and none of the handlers were written for that.
        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.Fields.dialPassthruEventUrl, String.class),
                response::setDialPassthruEventUrl);

        ExotelConnectAppletResponse.ParallelRinging parallelRinging = new ExotelConnectAppletResponse.ParallelRinging();

        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.ParallelRinging.Fields.activate, Boolean.class),
                parallelRinging::setActivate);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.ParallelRinging.Fields.maxParallelAttempts, Integer.class),
                parallelRinging::setMaxParallelAttempts);

        response.setParallelRinging(parallelRinging);
    }

    public Mono<ExotelCall> processCallStatusCallback(MessageAccess access, ExotelCallStatusCallback callback) {

        if (callback.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        return FlatMapUtil.flatMapMono(
                        () -> this.resolveCallbackTarget(callback.getCallSid()),
                        exotelCall -> super.updateInternalWithoutUser(
                                MessageAccess.of(exotelCall.getAppCode(), exotelCall.getClientCode(), true),
                                exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendCallStatusEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .then(this.handOverToOwner(updated))
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processCallStatusCallback"));
    }

    public Mono<ExotelCall> processPassThruCallback(MessageAccess access, ExotelPassThruCallback callback) {

        if (callback.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        return FlatMapUtil.flatMapMono(
                        () -> this.resolveCallbackTarget(callback.getCallSid()),
                        exotelCall -> super.updateInternalWithoutUser(
                                MessageAccess.of(exotelCall.getAppCode(), exotelCall.getClientCode(), true),
                                exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendPassthruCallbackEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .then(this.handOverToOwner(updated))
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processPassThruCallback"));
    }
}
