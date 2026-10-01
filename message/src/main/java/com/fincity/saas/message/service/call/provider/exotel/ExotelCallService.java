package com.fincity.saas.message.service.call.provider.exotel;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
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
import com.fincity.saas.message.service.call.ICallRecordingService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO>
        implements IBrowserCallService, ICallRecordingService {

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    private static final String PARAM_USER_ID = "userId";
    private static final String OPERATION_PLAY = "recording download";

    /** Domains a recording may be fetched from with account credentials; see {@link ExotelApiConfig#isRecordingUrl}. */
    @Value("${message.call.exotel.recording-domains:" + ExotelApiConfig.DEFAULT_RECORDING_DOMAINS + "}")
    private String[] recordingDomains = {ExotelApiConfig.DEFAULT_RECORDING_DOMAINS};

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

    // IBrowserCallService, delegated to ExotelIntegrationsService. Implemented here because CallService
    // discovers the capability with instanceof on the provider it already registers.

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
     * Places a browser call for a service that has already checked the deal. Returns the provider-shaped call
     * because the caller keys its record on the {@code Sid}; without it the later callback matches nothing and
     * the call is recorded twice.
     */
    @Override
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
     * Builds the row from what the provider reported. Its virtual number and SIP endpoint win over our endpoint
     * rows, which can be stale.
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

        if (result.getFromNumber() != null && !result.getFromNumber().isBlank()) call.setFrom(result.getFromNumber());

        // A blank status leaves the column null for the first callback to fill.
        ExotelCallStatus reported = ExotelCallStatus.lookupLiteral(result.getCallState());
        if (reported != null) call.setExotelCallStatus(reported);

        return call;
    }

    /**
     * Finds the call a callback belongs to, by the provider's call id, which the dial response supplies
     * synchronously.
     *
     * <p>Empty is a real outcome: an agent's browser token can reach the dial API directly, so some calls were
     * never recorded here. Failing would make the provider retry in vain and lose the rest of the batch.
     *
     * <p>Deliberately not scoped to a tenant: callback URLs often resolve to {@code SYSTEM} while the call
     * belongs to a sub-client. The row found supplies the access for the follow-up write. The cost: both callback
     * routes are {@code permitAll}, so anyone holding a {@code CallSid} (it is in every recording URL) can drive an
     * update on that row; the endpoint needs authenticating, which is a known deferral.
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
     * <p>A failed enqueue fails the callback on purpose: with no outbox row there is nothing for the sweeper to
     * retry, while the provider's re-send is idempotent on the call id.
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
                // Ours, never Exotel's: Exotel's needs the account's credentials, so a browser gets a sign-in prompt.
                .setRecordingUrl(ICallRecordingService.recordingUri(
                        call.getCode(), !StringUtil.safeIsBlank(call.getRecordingUrl())));

        String owner = call.getOwnerService() == null ? this.defaultCallOwnerService : call.getOwnerService();

        return this.eventDispatcher.enqueueAndDispatch(
                MessageAccess.of(call.getAppCode(), call.getClientCode(), Boolean.TRUE),
                owner,
                DispatchEventType.CALL_STATUS,
                call.getSid(),
                dispatch);
    }

    // Recordings

    /**
     * Streams a call's recording from Exotel with the credentials of the connection the call row names, since
     * Exotel's URL answers {@code 401} to a browser. Read within the caller's own app and client. Not available
     * when the row has no recording, its URL is not on an Exotel domain, or no call row names its connection.
     */
    @Override
    public Mono<ResponseEntity<Flux<DataBuffer>>> recording(MessageAccess access, String callCode, String range) {

        return FlatMapUtil.flatMapMono(
                        () -> super.dao.readInternal(access, callCode),
                        exotelCall -> this.isRecordingUrl(exotelCall.getRecordingUrl())
                                ? super.callService
                                        .readByExotelCallId(access, exotelCall.getId())
                                        .filter(call -> !StringUtil.safeIsBlank(call.getConnectionName()))
                                        .switchIfEmpty(Mono.defer(() -> this.recordingUnavailable(callCode)))
                                : this.recordingUnavailable(callCode),
                        (exotelCall, call) -> super.callConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), call.getConnectionName()),
                        (exotelCall, call, connection) -> super.isValidConnection(connection),
                        (exotelCall, call, connection, vConn) ->
                                this.fetchRecording(connection, callCode, exotelCall.getRecordingUrl(), range))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.recording"));
    }

    private boolean isRecordingUrl(String url) {
        return ExotelApiConfig.isRecordingUrl(url, Arrays.asList(this.recordingDomains));
    }

    /**
     * Fetches a recording with Basic credentials. Anything that is not audio, including a redirect (not followed),
     * is answered as not available; a failure to reach Exotel is a 502 that names no URL.
     */
    Mono<ResponseEntity<Flux<DataBuffer>>> fetchRecording(
            Connection connection, String callCode, String url, String range) {

        return this.webClientConfig
                .createExotelRecordingWebClient(connection)
                .flatMap(client -> client.get()
                        .uri(URI.create(url.trim()))
                        .headers(headers -> {
                            if (!StringUtil.safeIsBlank(range)) headers.set(HttpHeaders.RANGE, range);
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.releaseBody()
                                .then(this.recordingUnavailable(callCode)))
                        .toEntityFlux(DataBuffer.class))
                .flatMap(entity ->
                        ICallRecordingService.isAudio(entity.getHeaders().getContentType())
                                ? Mono.just(ICallRecordingService.asPlayable(entity))
                                : entity.getBody()
                                        .doOnNext(DataBufferUtils::release)
                                        .then(this.<ResponseEntity<Flux<DataBuffer>>>recordingUnavailable(callCode)))
                .onErrorResume(
                        e -> !(e instanceof GenericException),
                        e -> super.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                                MessageResourceService.EXOTEL_REQUEST_FAILED,
                                OPERATION_PLAY,
                                "no answer"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.fetchRecording"));
    }

    private <T> Mono<T> recordingUnavailable(String callCode) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                MessageResourceService.CALL_RECORDING_NOT_AVAILABLE,
                callCode);
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

        return FlatMapUtil.flatMapMono(
                        () -> this.toExotelRequest(callRequest, connection),
                        exotelCallRequest -> super.isValidConnection(connection),
                        (exotelCallRequest, vConn) -> this.makeExotelCall(
                                access,
                                access.getUserId(),
                                access.getUser() == null
                                        ? null
                                        : access.getUser().getPhoneNumber(),
                                exotelCallRequest,
                                connection),
                        (exotelCallRequest, vConn, eCreated) ->
                                this.toCall(eCreated).map(call -> call.setConnectionName(connection.getName())),
                        (exotelCallRequest, vConn, eCreated, call) -> super.callService.createInternal(access, call),
                        (exotelCallRequest, vConn, eCreated, call, cCall) -> super.callEventService
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
     *
     * <p>The agent is the request's {@code userId}: the service's own access carries no user.
     */
    @Override
    public Mono<ExotelCall> makeCallInternal(
            String appCode, String clientCode, CallRequest callRequest, String ownerService) {

        if (callRequest.getUserId() == null) return super.throwMissingParam(PARAM_USER_ID);

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);
        ULong userId = callRequest.getUserId();

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(
                                appCode, clientCode, callRequest.getConnectionName()),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) -> this.toExotelRequest(callRequest, connection),
                        (connection, vConn, exotelRequest) -> super.getUserIdAndPhone(clientCode, userId),
                        (connection, vConn, exotelRequest, agent) -> this.makeExotelCall(
                                access,
                                userId,
                                agent.getValue() == null
                                        ? null
                                        : agent.getValue().getNumber(),
                                exotelRequest,
                                connection),
                        (connection, vConn, exotelRequest, agent, eCreated) ->
                                super.updateInternalWithoutUser(access, eCreated.setOwnerService(ownerService)),
                        (connection, vConn, exotelRequest, agent, eCreated, stamped) -> this.toCall(stamped)
                                .map(call -> call.setConnectionName(connection.getName()))
                                .flatMap(call -> super.callService.createInternal(access, userId, call))
                                .thenReturn(stamped))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCallInternal"));
    }

    /**
     * The request Exotel is sent, for both make routes, checked before anything is looked up. A missing
     * {@code toNumber} is how an unparseable deal number arrives, and it is a 400 naming {@code to}.
     */
    private Mono<ExotelCallRequest> toExotelRequest(CallRequest callRequest, Connection connection) {

        String to = callRequest.getToNumber() == null
                ? null
                : callRequest.getToNumber().getNumber();
        if (StringUtil.safeIsBlank(to)) return super.throwMissingParam(ExotelCallRequest.Fields.to);

        String callerId = callRequest.getCallerId() == null
                ? super.getConnectionDetail(
                        connection.getConnectionDetails(), ExotelCallRequest.Fields.callerId, String.class)
                : callRequest.getCallerId().getLandlineNumber();
        if (StringUtil.safeIsBlank(callerId)) return super.throwMissingParam(ExotelCallRequest.Fields.callerId);

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());
        return Mono.just(exotelCallRequest);
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

    /** Asks Exotel to ring the agent, then the customer, and records the call as the agent's. */
    private Mono<ExotelCall> makeExotelCall(
            MessageAccess messageAccess, ULong userId, String agentPhone, ExotelCallRequest request, Connection conn) {

        if (StringUtil.safeIsBlank(agentPhone)) return super.throwMissingParam(ExotelCallRequest.Fields.from);

        PhoneNumber from = PhoneUtil.parse(agentPhone);

        if (from == null || from.getNumber() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.EXOTEL_INVALID_PHONE_NUMBER,
                    ExotelCallRequest.Fields.from,
                    agentPhone);

        request.setFrom(from.getNumber());

        return FlatMapUtil.flatMapMono(
                        () -> this.statusCallbackUrl(conn),
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
                                messageAccess,
                                userId,
                                ExotelCall.ofOutbound(request).update(response)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeExotelCall"));
    }

    /**
     * Where Exotel posts a click-to-call's status: the connection's {@code statusCallback}, else its
     * {@code callbackUrl}, verbatim; the tenant's app URL only when it states neither.
     */
    private Mono<String> statusCallbackUrl(Connection conn) {
        String stated = statusCallbackOf(conn.getConnectionDetails());
        return stated != null ? Mono.just(stated) : super.getCallBackAppUrl(conn.getAppCode());
    }

    /** The status URL a connection states, or null when it states none. */
    static String statusCallbackOf(Map<String, Object> details) {

        if (details == null) return null;

        for (String key : List.of(ExotelCallRequest.Fields.statusCallback, ExotelIntegrationsApiConfig.CALLBACK_URL)) {
            if (details.get(key) instanceof String url && !url.isBlank()) return url.trim();
        }

        return null;
    }

    @Override
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
                        connection -> super.getUserIdAndPhone(access.getClientCode(), request.getUserId()),
                        // A repeated CallSid answers with the destination again rather than erroring: the applet
                        // is retried, and the existing row means nothing is written twice.
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
                                            // The connect applet is always answered by the owning service.
                                            .setOwnerService(this.defaultCallOwnerService);

                                    // The call row is written after the Exotel row so it carries that row's id,
                                    // which is how the recording finds its connection.
                                    Mono<ExotelCall> exotelCreated = this.createInternal(
                                                    access, user.getId(), exotelCall)
                                            .flatMap(created -> this.toCall(created)
                                                    .map(call -> call.setConnectionName(connection.getName()))
                                                    .flatMap(call -> super.callService.createInternal(
                                                            access, user.getId(), call))
                                                    .thenReturn(created));

                                    Mono<ExotelConnectAppletResponse> responseCreated = this.createResponse(
                                            access,
                                            user.getId(),
                                            connection,
                                            user.getValue().getNumber());

                                    return Mono.zip(exotelCreated, responseCreated)
                                            .<ExotelConnectAppletResponse>flatMap(tuple -> super.callEventService
                                                    .sendIncomingCallEvent(
                                                            access.getAppCode(),
                                                            access.getClientCode(),
                                                            user.getId(),
                                                            tuple.getT1())
                                                    .thenReturn(tuple.getT2()));
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.connectCall"));
    }

    private Mono<ExotelConnectAppletResponse> createResponse(
            MessageAccess access, ULong userId, Connection connection, String fallbackPhone) {

        ExotelConnectAppletResponse response = new ExotelConnectAppletResponse();
        this.applyConnectionDetailsToResponse(response, connection.getConnectionDetails());

        // Stored PRIORITY order is the routing rule (see destinationNumbers).
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
     * The destinations the applet dials, given what is stored for the agent. Pure so it is testable, since it
     * changes inbound routing for every tenant; a tenant with only a {@code PSTN_PHONE} row still rings just that.
     *
     * <p>Stored order is returned untouched: ringing is sequential, so {@code PRIORITY} is the routing rule. The
     * profile number is a fallback for having no rows, not an entry to append, which would double-ring when it
     * disagrees with {@code PSTN_PHONE}. So an agent with only a SIP row is unreachable when their browser is shut.
     */
    static List<String> destinationNumbers(List<String> storedEndpoints, String fallbackPhone) {
        return storedEndpoints.isEmpty() ? fallbackOnly(fallbackPhone) : storedEndpoints;
    }

    /**
     * The agent's own number, for an agent with no provisioned endpoints, so they still ring. Empty when even
     * that is unknown, which the provider treats as nowhere to send the call.
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

        // Optional and unset by default: inbound status already arrives via the Passthru applet configured in the
        // provider's console, and setting both would deliver every event twice.
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
