package com.fincity.saas.message.service.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.call.CallDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
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
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
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

    private final CallConnectionService connectionService;
    private final ExotelCallService exotelCallService;

    private final EnumMap<ConnectionSubType, ICallService<?>> services = new EnumMap<>(ConnectionSubType.class);

    /**
     * Providers that additionally support browser calling.
     *
     * <p>Derived from {@link #services} rather than maintained by hand, so a provider that gains the
     * capability gets it here by implementing the interface, and one that never had it cannot be
     * half-registered.
     */
    private final EnumMap<ConnectionSubType, IBrowserCallService> browserServices =
            new EnumMap<>(ConnectionSubType.class);

    public CallService(CallConnectionService connectionService, ExotelCallService exotelCallService) {
        this.connectionService = connectionService;
        this.exotelCallService = exotelCallService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.EXOTEL, exotelCallService);

        this.services.forEach((subType, service) -> {
            if (service instanceof IBrowserCallService browserService)
                this.browserServices.put(subType, browserService);
        });
    }

    /**
     * The browser-calling implementation for a connection's provider.
     *
     * <p>Resolved from the connection rather than from a parameter: the connection already names the
     * provider through its subtype, so nothing else has to carry it and nothing can disagree with it.
     */
    public Mono<IBrowserCallService> browserServiceFor(Connection connection) {
        IBrowserCallService service = this.browserServices.get(connection.getConnectionSubType());

        if (service == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.BROWSER_CALLING_NOT_SUPPORTED,
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

    /**
     * Resolves the connection a browser-calling request names, then the provider behind it.
     *
     * <p>Every method below needs the same two things, and neither is worth repeating seven times.
     */
    private Mono<Tuple2<MessageAccess, Connection>> accessAndConnection(String connectionName) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.connectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), connectionName),
                (access, connection) -> Mono.just(Tuples.of(access, connection)));
    }

    /**
     * Registers this tenant's integration app with the calling provider.
     *
     * <p>Owner-gated: it spends the tenant's provider credentials and creates a billable object on
     * their account. {@code ROLE_Owner} rather than an {@code Entity_ACTION} authority because this
     * is tenant configuration, not CRUD on a row the caller owns — the same reasoning, and the same
     * authority, as WhatsApp session setup.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<CallAppStatus> initializeCallApp(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.initializeApp(tuple.getT1(), tuple.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.initializeCallApp"));
    }

    /**
     * Maps one agent to a browser-reachable endpoint.
     *
     * <p>Owner-gated, and the implementation must additionally confirm the <i>target</i> user
     * belongs to a client this caller manages. Being an owner says nothing about whose user this is:
     * without that check an owner in one tenant can mint SIP credentials for a user in another.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<ProvisionedAgent> provisionAgent(ProvisionAgentRequest request) {
        return this.accessAndConnection(request.getConnectionName())
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.provisionAgent(tuple.getT1(), tuple.getT2(), request)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.provisionAgent"));
    }

    /**
     * Tears down the tenant's integration app at the provider.
     *
     * <p>Owner-gated like setup, and destructive: every agent on the connection loses their softphone.
     */
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

    /**
     * Whether the tenant's calling app is set up on this connection.
     *
     * <p>Owner-gated like the rest of provisioning, and for the same reason rather than out of
     * symmetry: it names the app registered on the tenant's provider account and the callback URL
     * it answers on. Neither is a secret, and neither is any ordinary agent's business.
     */
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
     * Mints a browser calling credential for the caller.
     *
     * <p>Authenticated only, and the agent is taken from the token rather than from the request.
     * A {@code userId} parameter here would let any agent mint another agent's SIP credentials.
     */
    public Mono<BrowserCallToken> browserToken(String connectionName) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.generateBrowserToken(
                                tuple.getT1(), tuple.getT2(), tuple.getT1().getUserId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.browserToken"));
    }

    /** Whether the caller can take calls in the browser. Same reasoning on the agent identity. */
    public Mono<BrowserCallStatus> browserStatus(String connectionName, boolean verifyWithProvider) {
        return this.accessAndConnection(connectionName)
                .flatMap(tuple -> this.browserServiceFor(tuple.getT2())
                        .flatMap(service -> service.browserCallStatus(
                                tuple.getT1(), tuple.getT2(), tuple.getT1().getUserId(), verifyWithProvider)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.browserStatus"));
    }

    public Mono<Call> makeCall(CallRequest callRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.connectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), callRequest.getConnectionName()),
                        (access, connection) -> services.get(connection.getConnectionSubType())
                                .makeCall(access, callRequest, connection))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallService.makeCall"));
    }
}
