package com.fincity.saas.message.service.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.call.CallDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.BrowserDialRequest;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import com.fincity.saas.message.service.call.provider.telecmi.TelecmiCallService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import org.jooq.types.ULong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class CallService extends BaseUpdatableService<MessageCallsRecord, Call, CallDAO> {

    private static final String CALL_CACHE = "call";

    /** Optional connection detail: the URL the softphone loads the provider's calling library from. */
    public static final String CONNECTION_DETAIL_SDK_URL = "sdkUrl";

    private final CallConnectionService connectionService;
    private final ProviderUserEndpointDAO endpointDAO;
    private final ExotelCallService exotelCallService;
    private final TelecmiCallService telecmiCallService;

    private final EnumMap<ConnectionSubType, ICallService<?>> services = new EnumMap<>(ConnectionSubType.class);

    /** Derived from {@link #services} in {@link #init} by interface, never registered by hand. */
    private final EnumMap<ConnectionSubType, IBrowserCallService> browserServices =
            new EnumMap<>(ConnectionSubType.class);

    private final EnumMap<ConnectionSubType, ICallRecordingService> recordingServices =
            new EnumMap<>(ConnectionSubType.class);

    public CallService(
            CallConnectionService connectionService,
            ProviderUserEndpointDAO endpointDAO,
            ExotelCallService exotelCallService,
            TelecmiCallService telecmiCallService) {
        this.connectionService = connectionService;
        this.endpointDAO = endpointDAO;
        this.exotelCallService = exotelCallService;
        this.telecmiCallService = telecmiCallService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.EXOTEL, exotelCallService);
        this.services.put(ConnectionSubType.TELECMI, telecmiCallService);

        this.services.forEach((subType, service) -> {
            if (service instanceof IBrowserCallService browserService)
                this.browserServices.put(subType, browserService);
            if (service instanceof ICallRecordingService recordingService)
                this.recordingServices.put(subType, recordingService);
        });
    }

    /** The browser-calling implementation for the provider the connection's subtype names. */
    public Mono<IBrowserCallService> browserServiceFor(Connection connection) {
        IBrowserCallService service = this.browserServices.get(connection.getConnectionSubType());

        if (service == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.BROWSER_CALLING_NOT_SUPPORTED,
                    connection.getConnectionSubType());

        return Mono.just(service);
    }

    /** The calling implementation for a connection's provider, or a bad request when no provider serves it. */
    public Mono<ICallService<?>> callServiceFor(Connection connection) {
        ICallService<?> service = this.services.get(connection.getConnectionSubType());

        if (service == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.CALLING_NOT_SUPPORTED,
                    connection.getConnectionSubType());

        return Mono.just(service);
    }

    @Override
    protected String getCacheName() {
        return CALL_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.CALL;
    }

    /** The caller's access and the connection a browser-calling request names. */
    private Mono<Tuple2<MessageAccess, Connection>> accessAndConnection(String connectionName) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.connectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                (access, connection) -> Mono.just(Tuples.of(access, connection)));
    }

    /**
     * Registers this tenant's integration app with the calling provider. Owner-gated: it spends the tenant's
     * provider credentials and creates a billable object on their account.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<CallAppStatus> initializeCallApp(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.initializeApp(tuple.getT1(), tuple.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.initializeCallApp"));
    }

    /**
     * Maps one agent to a browser-reachable endpoint. The implementation must also confirm the target user belongs
     * to a client this caller manages, or an owner in one tenant could mint SIP credentials for a user in another.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<ProvisionedAgent> provisionAgent(ProvisionAgentRequest request) {
        return this.accessAndConnection(request.getConnectionName())
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.provisionAgent(tuple.getT1(), tuple.getT2(), request)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.provisionAgent"));
    }

    /** Tears down the tenant's app at the provider; every agent on the connection loses their softphone. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<Boolean> teardownCallApp(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.teardownApp(tuple.getT1(), tuple.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.teardownCallApp"));
    }

    /** Every agent provisioned on a connection. Owner-gated: it exposes each agent's endpoints. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Flux<ProvisionedAgent> getAgentEndpoints(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMapMany(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMapMany(service -> service.getAgentEndpoints(tuple.getT1(), tuple.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.getAgentEndpoints"));
    }

    /** Whether the tenant's calling app is set up. Owner-gated: it names the provider app and its callback URL. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<CallAppStatus> callAppStatus(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple ->
                        this.browserServiceFor(tuple.getT2()).flatMap(service -> service.callAppStatus(tuple.getT1())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.callAppStatus"));
    }

    /** Retires an agent's endpoints. Belongs in offboarding. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<Integer> deactivateAgent(String connectionName, ULong userId) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.deactivateAgent(tuple.getT1(), tuple.getT2(), userId)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.deactivateAgent"));
    }

    /**
     * Mints a browser calling credential for the caller. The agent comes from the token, never the request, so no
     * agent can mint another agent's SIP credentials.
     */
    public Mono<BrowserCallToken> browserToken(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.generateBrowserToken(
                                tuple.getT1(), tuple.getT2(), tuple.getT1().getUserId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.browserToken"));
    }

    /**
     * Whether the caller, taken from the token, can take calls in the browser. With no {@code connectionName} it
     * answers for the connection the caller is provisioned on; an agent on several gets an error naming them.
     */
    public Mono<BrowserCallStatus> browserStatus(String connectionName, boolean verifyWithProvider) {

        if (connectionName != null && !connectionName.isBlank())
            return this.connectionBrowserStatus(connectionName, verifyWithProvider);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.endpointDAO
                                .findActiveConnectionNames(
                                        access.getAppCode(),
                                        access.getUserId(),
                                        ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                                .collectList(),
                        (access, names) -> switch (names.size()) {
                            case 0 -> Mono.just(BrowserCallStatus.notProvisioned(null));
                            case 1 -> this.connectionBrowserStatus(names.getFirst(), verifyWithProvider);
                            default ->
                                super.msgService.<BrowserCallStatus>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.BROWSER_CONNECTION_AMBIGUOUS,
                                        String.join(", ", names));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.browserStatus"));
    }

    private Mono<BrowserCallStatus> connectionBrowserStatus(String connectionName, boolean verifyWithProvider) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.browserCallStatus(
                                tuple.getT1(), tuple.getT2(), tuple.getT1().getUserId(), verifyWithProvider))
                        .map(status -> withConnection(status, tuple.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.connectionBrowserStatus"));
    }

    /** Stamps what the softphone needs from the connection itself, whichever provider answered. */
    static BrowserCallStatus withConnection(BrowserCallStatus status, Connection connection) {

        Object sdkUrl = connection.getConnectionDetails() == null
                ? null
                : connection.getConnectionDetails().get(CONNECTION_DETAIL_SDK_URL);

        return status.setConnectionName(connection.getName())
                .setSdkUrl(sdkUrl instanceof String url && !url.isBlank() ? url : null);
    }

    /**
     * A call's recording. Through {@code hasAccess}, so a user without a phone number on their profile is refused
     * ({@code phone_number_required}), a known limitation for listening.
     */
    public Mono<ResponseEntity<Flux<DataBuffer>>> recording(String callCode, String range) {
        return super.hasAccess()
                .flatMap(access -> this.recording(access, callCode, range))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.recording"));
    }

    /**
     * Plays a call's recording. The URL carries only our random call code, so each provider is asked in turn within
     * the caller's app and client; a code none has, another tenant's included, is not found.
     */
    public Mono<ResponseEntity<Flux<DataBuffer>>> recording(MessageAccess access, String callCode, String range) {
        // Chained, not concatMap().next(): next() cancels the provider that answered, which can release the
        // connection its recording streams over.
        Mono<ResponseEntity<Flux<DataBuffer>>> result = Mono.empty();
        for (ICallRecordingService service : this.recordingServices.values()) {
            result = result.switchIfEmpty(Mono.defer(() -> service.recording(access, callCode, range)));
        }
        return result
                .switchIfEmpty(Mono.defer(() -> super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        MessageResourceService.CALL_RECORDING_NOT_AVAILABLE,
                        callCode)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.recording(access)"));
    }

    /** The call row an Exotel call was recorded under, which names its connection. */
    public Mono<Call> readByExotelCallId(MessageAccess access, ULong exotelCallId) {
        return super.dao.findByExotelCallId(access.getAppCode(), access.getClientCode(), exotelCallId);
    }

    public Mono<Call> makeCall(CallRequest callRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.connectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), callRequest.getConnectionName()),
                        (access, connection) -> this.callServiceFor(connection)
                                .flatMap(service -> service.makeCall(access, callRequest, connection)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCall"));
    }

    // Service-to-service operations, reached only through /api/message/call/internal. None checks access: the
    // caller already decided the call may be made, and this service cannot evaluate a deal to decide again.

    /** Places a call through whichever provider the named connection uses. */
    public Mono<Object> makeCallInternal(
            String appCode, String clientCode, CallRequest callRequest, String ownerService) {
        return this.connectionService
                .getCoreDocument(appCode, clientCode, callRequest.getConnectionName())
                .flatMap(this::callServiceFor)
                .<Object>flatMap(service -> service.makeCallInternal(appCode, clientCode, callRequest, ownerService))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCallInternal"));
    }

    /** Rings an agent's browser and then the customer, through the named connection's provider. */
    public Mono<Object> browserDialInternal(String appCode, String clientCode, BrowserDialRequest request) {
        return this.connectionService
                .getCoreDocument(appCode, clientCode, request.getConnectionName())
                .flatMap(this::browserServiceFor)
                .<Object>flatMap(service -> service.browserDialInternal(
                        appCode,
                        clientCode,
                        request.getConnectionName(),
                        ULongUtil.valueOf(request.getUserId()),
                        request.getToNumber()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.browserDialInternal"));
    }

    /** Answers an inbound call's provider request, in that provider's own reply format. */
    public Mono<Object> connectCall(String appCode, String clientCode, IncomingCallRequest request) {
        return this.connectionService
                .getCoreDocument(appCode, clientCode, request.getConnectionName())
                .flatMap(this::callServiceFor)
                .<Object>flatMap(service -> service.connectCall(appCode, clientCode, request))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.connectCall"));
    }
}
