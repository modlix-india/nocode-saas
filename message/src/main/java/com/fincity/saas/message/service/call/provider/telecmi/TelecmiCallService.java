package com.fincity.saas.message.service.call.provider.telecmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.provider.telecmi.TelecmiDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffect;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.jooq.tables.records.MessageTelecmiCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiWebhook;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiFlowReply;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.IBrowserCallService;
import com.fincity.saas.message.service.call.ICallRecordingService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import com.fincity.saas.message.util.PhoneUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * TeleCMI as a CALL provider, shaped like {@code ExotelCallService}. Outbound calls ring the agent first
 * (softphone, or mobile through Follow Me), then the customer; inbound calls are answered through the HTTP flow.
 */
@Service
public class TelecmiCallService extends AbstractCallProviderService<MessageTelecmiCallsRecord, TelecmiCall, TelecmiDAO>
        implements IBrowserCallService, ICallRecordingService {

    public static final String TELECMI_PROVIDER_URI = "/telecmi";
    private static final String TELECMI_CALL_CACHE = "telecmiCall";

    private static final String PARAM_USER_ID = "userId";
    private static final String PARAM_PROVIDER_REQUEST = "providerIncomingRequest";
    private static final String OPERATION_DIRECT = "calls placed outside a deal";

    @Value("${message.call.default-owner-service:entity-processor}")
    private String defaultCallOwnerService;

    private TelecmiIntegrationsService integrationsService;

    private EventDispatcher eventDispatcher;

    @Autowired
    public void setEventDispatcher(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @Autowired
    public void setIntegrationsService(TelecmiIntegrationsService integrationsService) {
        this.integrationsService = integrationsService;
    }

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

    /** Rings the agent's softphone, then the customer; the caller keys its record on {@code providerCallId}. */
    @Override
    public Mono<TelecmiCall> browserDialInternal(
            String appCode, String clientCode, String connectionName, ULong userId, String toNumber) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(appCode, clientCode, connectionName),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) ->
                                this.integrationsService.prepareDial(access, connection, userId, toNumber, null, true),
                        (connection, vConn, dial) ->
                                this.placeCall(access, userId, connection, dial, this.defaultCallOwnerService))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.browserDialInternal"));
    }

    /** Rings the {@code userId} agent's mobile through Follow Me, then the customer; TeleCMI needs an agent. */
    @Override
    public Mono<TelecmiCall> makeCallInternal(
            String appCode, String clientCode, CallRequest callRequest, String ownerService) {

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        String toNumber = callRequest.getToNumber() == null
                ? null
                : callRequest.getToNumber().getNumber();
        String callerId = callRequest.getCallerId() == null
                ? null
                : callRequest.getCallerId().getNumber();

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(
                                appCode, clientCode, callRequest.getConnectionName()),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) -> this.integrationsService.prepareDial(
                                access, connection, callRequest.getUserId(), toNumber, callerId, false),
                        (connection, vConn, dial) -> this.placeCall(
                                access,
                                callRequest.getUserId(),
                                connection,
                                dial,
                                ownerService == null ? this.defaultCallOwnerService : ownerService))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.makeCallInternal"));
    }

    /**
     * Writes the row before asking TeleCMI to ring, so no webhook arrives for a call we have no row for. A refused
     * or failed request marks the row {@code FAILED} before the error is returned.
     */
    private Mono<TelecmiCall> placeCall(
            MessageAccess access,
            ULong userId,
            Connection connection,
            TelecmiIntegrationsService.TelecmiDial dial,
            String ownerService) {

        return FlatMapUtil.flatMapMono(
                        () -> this.createInternal(access, userId, dial.call().setOwnerService(ownerService)),
                        created -> this.integrationsService
                                .clickToCall(connection, dial.request())
                                .onErrorResume(e ->
                                        this.markFailed(access, created, null).then(Mono.error(e))),
                        (created, response) -> this.recordDial(access, created, response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.placeCall"));
    }

    private Mono<TelecmiCall> recordDial(MessageAccess access, TelecmiCall call, TelecmiResponse response) {

        if (response.isSuccess() && !StringUtil.safeIsBlank(response.getRequestId()))
            return super.dao
                    .recordPlacement(call.getId(), response.getRequestId(), response.toRecorded())
                    .flatMap(updated -> this.evictCache(call).thenReturn(updated));

        return this.markFailed(access, call, response).then(this.integrationsService.clickToCallRefused(response));
    }

    private Mono<TelecmiCall> markFailed(MessageAccess access, TelecmiCall call, TelecmiResponse response) {
        return super.dao
                .markPlacementFailed(call.getId(), response == null ? null : response.toRecorded())
                .flatMap(updated -> this.evictCache(call).thenReturn(updated));
    }

    /**
     * Takes one webhook into the call's row; the token and app id prove the tenant before the body is trusted.
     *
     * <p>Handed over once per call, not on every webhook: the outbox is unique on the call id, so a queued
     * progress event would make the outcome drop as a redelivery. A webhook for no call of ours is ignored.
     */
    public Mono<TelecmiCall> processWebhook(MessageAccess access, String token, JsonNode body) {

        TelecmiWebhook webhook = TelecmiWebhook.of(body);

        return FlatMapUtil.flatMapMono(
                        () -> this.integrationsService.verifyWebhook(access, token, webhook.appId()),
                        app -> this.findCall(access, webhook),
                        (app, call) -> Mono.just(TelecmiWebhookEffect.of(call, webhook, LocalDateTime.now())),
                        (app, call, effect) -> super.dao.apply(call.getId(), effect),
                        (app, call, effect, applied) -> this.evictCache(call).then(super.dao.readById(call.getId())),
                        (app, call, effect, applied, updated) -> this.notifyAndHandOver(updated, applied, effect))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.processWebhook"));
    }

    /**
     * By the per-call key (looked up across tenants, then held to this one), else by our {@code extra_params}
     * code for webhooks that beat the click-to-call response. A row whose code or key disagrees is not its row.
     */
    private Mono<TelecmiCall> findCall(MessageAccess access, TelecmiWebhook webhook) {

        String key = webhook.requestId() != null ? webhook.requestId() : webhook.conversationUuid();
        String callCode = webhook.callCode();

        Mono<TelecmiCall> byKey = key == null ? Mono.empty() : super.dao.findByUniqueField(key);
        Mono<TelecmiCall> byCode = callCode == null
                ? Mono.empty()
                : super.dao.findByCode(access.getAppCode(), access.getClientCode(), callCode);

        return byKey.switchIfEmpty(byCode)
                .filter(call -> access.getAppCode().equals(call.getAppCode())
                        && access.getClientCode().equals(call.getClientCode()))
                .filter(call -> callCode == null || callCode.equals(call.getCode()))
                .filter(call ->
                        call.getProviderCallId() == null || key == null || key.equals(call.getProviderCallId()));
    }

    /**
     * Hands over when this webhook carries the outcome, not only when it wrote it, so a retry after a failure
     * past the write still delivers. A repeat is harmless: the owner merges on the call id.
     */
    private Mono<TelecmiCall> notifyAndHandOver(
            TelecmiCall call, TelecmiDAO.Applied applied, TelecmiWebhookEffect effect) {

        Mono<Void> notify = call.getUserId() == null
                ? Mono.empty()
                : super.callEventService.sendCallStatusEvent(
                        call.getAppCode(), call.getClientCode(), call.getUserId(), call);

        boolean decided = TelecmiWebhookEffect.FINAL.contains(call.getCallStatus());
        boolean handOver = decided && (applied.finalised() || effect.finalStatus() != null || applied.recordingAdded());

        return notify.then(handOver ? this.handOverToOwner(call) : Mono.empty()).thenReturn(call);
    }

    private Mono<Void> handOverToOwner(TelecmiCall call) {

        String owner = call.getOwnerService() == null ? this.defaultCallOwnerService : call.getOwnerService();
        String key = call.getProviderCallId() != null ? call.getProviderCallId() : call.getCode();

        return this.eventDispatcher.enqueueAndDispatch(
                MessageAccess.of(call.getAppCode(), call.getClientCode(), Boolean.TRUE),
                owner,
                DispatchEventType.CALL_STATUS,
                key,
                this.toDispatch(call));
    }

    CallEventDispatch toDispatch(TelecmiCall call) {
        return new CallEventDispatch()
                .setProviderCallId(call.getProviderCallId())
                .setEventType(DispatchEventType.CALL_STATUS.name())
                .setCallProvider(this.getConnectionSubType().getProvider())
                .setConnectionName(call.getConnectionName())
                .setOutbound(call.getIsOutbound())
                .setFromDialCode(call.getFromDialCode())
                .setFrom(call.getFrom())
                .setToDialCode(call.getToDialCode())
                .setTo(call.getTo())
                .setCustomerDialCode(call.getCustomerDialCode())
                .setCustomerPhoneNumber(call.getCustomerPhoneNumber())
                .setCallerId(call.getCallerId())
                .setCallStatus(ownerVocabulary(call.getCallStatus()))
                .setNormalizedCallStatus(call.getCallStatus())
                .setLeg1Status(
                        legVocabulary(call.getLeg1Status(), call.getLeg1HangupReason(), call.getLeg1Cdr() != null))
                .setLeg2Status(
                        legVocabulary(call.getLeg2Status(), call.getLeg2HangupReason(), call.getLeg2Cdr() != null))
                .setStartTime(call.getStartTime())
                .setEndTime(call.getEndTime())
                .setDuration(call.getDuration())
                .setConversationDuration(call.getConversationDuration())
                .setRecordingUrl(ICallRecordingService.recordingUri(
                        call.getCode(), !StringUtil.safeIsBlank(call.getRecordingFile())));
    }

    /** The status in the words entity-processor's {@code ExotelCallStatus.of} reads; null for none. */
    static String ownerVocabulary(CallStatus status) {
        if (status == null) return null;
        return switch (status) {
            case QUEUED -> "queued";
            case ORIGINATE -> "in-progress";
            case COMPLETE -> "completed";
            case FAILED -> "failed";
            case BUSY -> "busy";
            case NO_ANSWER -> "no-answer";
            case CANCELED -> "cancelled";
            case UNKNOWN, INSUFFICIENT_BALANCE -> null;
        };
    }

    /** {@code answered} on an event means the leg is live; on a CDR, answered and ended. */
    static String legVocabulary(String status, String hangupReason, boolean fromCdr) {
        if (status == null) return null;
        if (!fromCdr)
            return "started".equalsIgnoreCase(status) || "answered".equalsIgnoreCase(status) ? "in-progress" : null;
        if ("answered".equalsIgnoreCase(status)) return "completed";
        if ("missed".equalsIgnoreCase(status))
            return TelecmiWebhookEffect.REASON_REJECTED.equalsIgnoreCase(hangupReason) ? "busy" : "no-answer";
        return null;
    }

    /**
     * Proxies the recording so the browser never holds the URL carrying the secret. Read within the caller's
     * tenant (deal access is entity-processor's check), and past the cache: the file name arrives by webhook.
     */
    @Override
    public Mono<ResponseEntity<Flux<DataBuffer>>> recording(MessageAccess access, String callCode, String range) {

        return FlatMapUtil.flatMapMono(
                        () -> super.dao
                                .readInternal(access, callCode)
                                .flatMap(call -> StringUtil.safeIsBlank(call.getRecordingFile())
                                        ? ICallRecordingService.<TelecmiCall>unavailable(super.msgService, callCode)
                                        : Mono.just(call)),
                        call -> super.callConnectionService.getCoreDocument(
                                call.getAppCode(), call.getClientCode(), call.getConnectionName()),
                        // As Exotel's: the secret goes only to a connection that is TeleCMI's.
                        (call, connection) -> super.isValidConnection(connection),
                        (call, connection, valid) -> this.integrationsService.fetchRecording(
                                connection, callCode, call.getRecordingFile(), range))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.recording(access)"));
    }

    /** Refused: the interface's empty default would answer 200 for a call nobody placed. */
    @Override
    public Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {
        return this.notAvailable(OPERATION_DIRECT);
    }

    /**
     * Answers the inbound HTTP flow with whom to ring. A repeated {@code cmiuuid} is answered again without
     * writing, since TeleCMI drops the caller if a retry fails; the reply is built before the row is written.
     * The origin is proven earlier by {@link #verifyInboundFlow}, before the owning service touches a deal.
     */
    @Override
    public Mono<TelecmiFlowReply> connectCall(String appCode, String clientCode, IncomingCallRequest request) {

        if (request.getUserId() == null) return super.throwMissingParam(PARAM_USER_ID);

        Map<String, String> flow = request.getProviderIncomingRequest();

        if (flow == null || flow.isEmpty()) return super.throwMissingParam(PARAM_PROVIDER_REQUEST);

        String cmiuuid = flow.get(TelecmiApiConfig.FLOW_CMIUUID);

        if (StringUtil.safeIsBlank(cmiuuid)) return super.throwMissingParam(TelecmiApiConfig.FLOW_CMIUUID);

        MessageAccess access = MessageAccess.of(appCode, clientCode, Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                        () -> super.callConnectionService.getCoreDocument(
                                appCode, clientCode, request.getConnectionName()),
                        connection -> super.isValidConnection(connection),
                        (connection, vConn) -> this.integrationsService.flowAppMatches(connection, flow)
                                ? super.getUserIdAndPhone(clientCode, request.getUserId())
                                : this.integrationsService.unauthorized(),
                        (connection, vConn, agent) ->
                                this.integrationsService.flowReply(access, connection, agent.getId(), agent.getValue()),
                        (connection, vConn, agent, reply) -> super.existsByUniqueField(access, cmiuuid)
                                .flatMap(exists -> Boolean.TRUE.equals(exists)
                                        ? Mono.just(reply)
                                        : this.recordInbound(access, connection, agent.getId(), cmiuuid, flow)
                                                .thenReturn(reply)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.connectCall"));
    }

    private Mono<Void> recordInbound(
            MessageAccess access, Connection connection, ULong userId, String cmiuuid, Map<String, String> flow) {

        TelecmiCall call = TelecmiCall.ofInbound(
                        cmiuuid,
                        connection.getName(),
                        PhoneUtil.parse(flow.get(TelecmiApiConfig.FLOW_FROM)),
                        PhoneUtil.parse(flow.get(TelecmiApiConfig.FLOW_TO)),
                        new HashMap<>(flow))
                .setOwnerService(this.defaultCallOwnerService);

        return this.createInternal(access, userId, call)
                .flatMap(created -> super.callEventService.sendIncomingCallEvent(
                        access.getAppCode(), access.getClientCode(), userId, created));
    }

    /** Proves an inbound flow request is this tenant's: the same token and app id check as the webhooks. */
    public Mono<Boolean> verifyInboundFlow(MessageAccess access, String token, String appId) {
        return this.integrationsService
                .verifyWebhook(access, token, appId)
                .thenReturn(Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TelecmiCallService.verifyInboundFlow"));
    }

    private <T> Mono<T> notAvailable(String operation) {
        return super.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_IMPLEMENTED, msg),
                MessageResourceService.CALL_OPERATION_NOT_AVAILABLE,
                this.getConnectionSubType().getProvider(),
                operation);
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.TELECMI_CALL;
    }

    @Override
    protected String getCacheName() {
        return TELECMI_CALL_CACHE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.TELECMI;
    }

    @Override
    public String getProviderUri() {
        return TELECMI_PROVIDER_URI;
    }
}
