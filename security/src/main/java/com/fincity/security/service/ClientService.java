package com.fincity.security.service;

import java.math.BigInteger;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.policy.ClientOtpPolicyService;
import com.fincity.security.service.policy.ClientPasswordPolicyService;
import com.fincity.security.service.policy.ClientPinPolicyService;
import com.fincity.security.service.policy.IPolicyService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;

@Service
public class ClientService
        extends AbstractSecurityUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

    public static final String CACHE_CLIENT_URL_LIST = "list";
    public static final String CACHE_NAME_CLIENT_URL = "clientUrl";
    public static final String CACHE_NAME_CLIENT_URI = "uri";
    public static final String CC = "clientCode";

    private static final String CACHE_NAME_CLIENT_TYPE_CODE_LEVEL = "clientTypeCodeLevel";
    private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";
    private static final String CACHE_NAME_MANAGED_CLIENT_INFO = "managedClientInfoById";
    private static final String CACHE_NAME_CLIENT_ID = "clientId";

    @Autowired
    private CacheService cacheService;

    @Autowired
    @Lazy
    private AppService appService;

    @Autowired
    private SecurityMessageResourceService securityMessageResourceService;

    @Autowired
    private AppRegistrationV2DAO appRegistrationDAO;

    @Autowired
    private ClientHierarchyService clientHierarchyService;

    @Autowired
    private ClientPasswordPolicyService clientPasswordPolicyService;

    @Autowired
    private ClientPinPolicyService clientPinPolicyService;

    @Autowired
    private ClientOtpPolicyService clientOtpPolicyService;

    @Value("${security.subdomain.endings}")
    private String[] subDomainURLEndings;

    private final EnumMap<AuthenticationPasswordType, IPolicyService<? extends AbstractPolicy>> policyServices = new EnumMap<>(
            AuthenticationPasswordType.class);

    @PostConstruct
    public void init() {
        this.policyServices.putAll(Map.of(
                clientPasswordPolicyService.getAuthenticationPasswordType(), clientPasswordPolicyService,
                clientPinPolicyService.getAuthenticationPasswordType(), clientPinPolicyService,
                clientOtpPolicyService.getAuthenticationPasswordType(), clientOtpPolicyService));
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.CLIENT;
    }

    public Mono<Client> getClientBy(ServerHttpRequest request) {

        HttpHeaders header = request.getHeaders();

        String uriScheme = header.getFirst("X-Forwarded-Proto");
        String uriHost = header.getFirst("X-Forwarded-Host");
        String uriPort = header.getFirst("X-Forwarded-Port");

        final URI uri1 = request.getURI();

        if (uriScheme == null)
            uriScheme = uri1.getScheme();
        if (uriHost == null)
            uriHost = uri1.getHost();
        if (uriPort == null)
            uriPort = "" + uri1.getPort();

        int ind = uriHost.indexOf(':');
        if (ind != -1)
            uriHost = uriHost.substring(0, ind);

        return getClientPattern(uriScheme, uriHost, uriPort).map(ClientUrlPattern::getIdentifier)
                .map(ULong::valueOf)
                .defaultIfEmpty(ULong.valueOf(1L))
                .flatMap(this::readInternal);
    }

    public Mono<List<Client>> getClientsBy(List<ULong> ids) {
        return this.dao.getClientsBy(ids);
    }

    public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {
        return this.clientHierarchyService.isBeingManagedBy(managingClientId, clientId);
    }

    public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {
        return this.clientHierarchyService.isBeingManagedBy(managingClientCode, clientCode);
    }

    public Mono<Boolean> isUserBeingManaged(String managingClientCode, ULong userId) {
        return this.clientHierarchyService.isUserBeingManaged(managingClientCode, userId);
    }

    public Mono<ClientUrlPattern> getClientPattern(String uriScheme, String uriHost, String uriPort) {

        return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_URI, () -> this.readAllAsClientURLPattern()
                        .flatMapIterable(e -> e)
                        .filter(e -> e.isValidClientURLPattern(uriHost, uriPort))
                        .take(1)
                        .collectList()
                        .flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e.getFirst()))
                        .switchIfEmpty(Mono.defer(() -> getClientPatternBySubdomain(uriHost))), uriScheme, uriHost, ":",
                uriPort);
    }

    private Mono<? extends ClientUrlPattern> getClientPatternBySubdomain(String uriHost) {

        if (this.subDomainURLEndings == null || this.subDomainURLEndings.length == 0)
            return Mono.empty();

        String code = null;
        for (String eachEnding : this.subDomainURLEndings) {
            if (uriHost.toLowerCase()
                    .endsWith(eachEnding)) {
                code = uriHost.substring(0, uriHost.length() - eachEnding.length());
                break;
            }
        }

        if (code == null)
            return Mono.empty();

        return this.appService.getAppByCode(code)
                .flatMap(app -> this.getClientInfoById(app.getClientId())
                        .map(client -> new ClientUrlPattern("", client.getCode(), uriHost, app.getAppCode())));
    }

    public Mono<List<ClientUrlPattern>> readAllAsClientURLPattern() {
        return cacheService.cacheEmptyValueOrGet(CACHE_NAME_CLIENT_URL, () -> this.dao.readClientPatterns()
                .collectList(), CACHE_CLIENT_URL_LIST);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractPolicy> Mono<T> getClientAppPolicy(ULong clientId, ULong appId,
                                                                 AuthenticationPasswordType passwordType) {

        IPolicyService<T> policyService = (IPolicyService<T>) policyServices.get(passwordType);

        return policyService.getClientAppPolicy(clientId, appId);
    }

    public <T extends AbstractPolicy> Mono<T> getClientAppPolicy(ULong clientId, String appCode,
                                                                 AuthenticationPasswordType passwordType) {
        return this.appService.getAppId(appCode).flatMap(appId -> getClientAppPolicy(clientId, appId, passwordType));
    }

    public Mono<Tuple3<String, String, String>> getClientTypeNCodeNClientLevel(ULong id) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_TYPE_CODE_LEVEL, () -> this.dao.getClientTypeNCode(id), id);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
    @Override
    public Mono<Client> create(Client entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> super.create(entity.setLevelType(Client.getChildClientLevelType(ca.getClientLevelType()))),

                (ca, client) -> {
                    if (!ca.isSystemClient())
                        return this.clientHierarchyService.create(ULongUtil.valueOf(ca.getUser().getClientId()), client.getId())
                                .map(x -> client);

                    return Mono.just(client);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.create"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_READ')")
    @Override
    public Mono<Client> read(ULong id) {
        return super.read(id);
    }

    public Mono<Client> readInternal(ULong id) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_ID, () -> this.dao.readInternal(id), id);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_READ')")
    @Override
    public Mono<Page<Client>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<Client> update(Client entity) {
        return super.update(entity).flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_CODE, entity.getId())
                .flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, entity.getId()))
                .map(x -> e));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<Client> update(ULong key, Map<String, Object> fields) {
        return super.update(key, fields).flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_CODE, e.getId())
                .flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, e.getId()))
                .map(x -> e));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return this.read(id)
                .map(e -> {
                    e.setStatusCode(SecurityClientStatusCode.DELETED);
                    return e;
                })
                .flatMap(this::update)
                .flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_CODE, id)
                        .flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, id)))
                .map(e -> 1);
    }

    @Override
    protected Mono<Client> updatableEntity(Client entity) {
        return Mono.just(entity);
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser()
                .map(ContextUser::getId)
                .map(ULong::valueOf);
    }

    public Mono<Boolean> validatePasswordPolicy(ULong clientId, ULong appId, ULong userId,
                                                AuthenticationPasswordType passwordType, String password) {
        return policyServices.get(passwordType).checkAllConditions(clientId, appId, userId, password)
                .switchIfEmpty(Mono.just(Boolean.TRUE));
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractPolicy> Mono<Boolean> validatePasswordPolicy(T policy, ULong userId,
                                                                           AuthenticationPasswordType passType, String password) {

        IPolicyService<T> service = (IPolicyService<T>) this.policyServices.get(passType);

        return service.checkAllConditions(policy, userId, password).switchIfEmpty(Mono.just(Boolean.TRUE));
    }

    public Mono<Boolean> validatePasswordPolicy(ULong clientId, String appCode, ULong userId,
                                                AuthenticationPasswordType passwordType, String password) {
        return this.appService.getAppByCode(appCode)
                .flatMap(app -> this.validatePasswordPolicy(clientId, app.getId(), userId, passwordType, password))
                .switchIfEmpty(Mono.just(Boolean.TRUE));
    }

    public Mono<Client> getClientInfoById(BigInteger id) {
        return this.getClientInfoById(ULong.valueOf(id));
    }

    public Mono<Client> getClientInfoById(ULong id) {
        return this.readInternal(id);
    }

    public Mono<Client> getManagedClientOfClientById(ULong clientId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_MANAGED_CLIENT_INFO,
                () -> this.clientHierarchyService.getManagingClient(clientId, ClientHierarchy.Level.ZERO)
                        .flatMap(this::getClientInfoById).defaultIfEmpty(new Client()),
                clientId);
    }

    public Mono<Client> getClientBy(String clientCode) {
        return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_CODE, () -> this.dao.getClientBy(clientCode), clientCode);
    }

    public Mono<ULong> getClientId(String clientCode) {
        return this.getClientBy(clientCode).map(Client::getId);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> makeClientActiveIfInActive(ULong clientId) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(clientId, ULong.valueOf(ca.getUser()
                                .getClientId()))),

                        (ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                                : this.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), id),

                        (ca, id, sysOrManaged) -> Boolean.TRUE.equals(sysOrManaged)
                                ? this.dao.makeClientActiveIfInActive(clientId)
                                : Mono.empty())

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.makeClientActiveIfInActive"))
                .switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "client"));

    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> makeClientInActive(ULong clientId) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(clientId, ULong.valueOf(ca.getUser()
                                .getClientId()))),

                        (ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                                : this.isBeingManagedBy(ULong.valueOf(ca.getUser()
                                .getClientId()), id),

                        (ca, id, sysOrManaged) -> Boolean.TRUE.equals(sysOrManaged) ? this.dao.makeClientInActive(clientId)
                                : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.makeClientIfInActive"))
                .switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "client"));
    }

    public Mono<ULong> getSystemClientId() {
        return this.cacheService.cacheValueOrGet("CACHE_SYSTEM_CLIENT_ID", () -> this.dao.getSystemClientId(),
                "SYSTEM");
    }

    public Mono<ClientLevelType> getClientLevelType(ULong clientId, ULong appId) {

        return FlatMapUtil.flatMapMono(

                () -> this.appService.getAppById(appId),

                app -> this.clientHierarchyService.getClientHierarchy(clientId),

                (app, clientHierarchy) -> {

                    if (!clientHierarchy.inClientHierarchy(clientId))
                        return Mono.empty();

                    if (app.getClientId().equals(clientId))
                        return Mono.just(ClientLevelType.OWNER);

                    if (app.getClientId().equals(clientHierarchy.getManageClientLevel0()))
                        return Mono.just(ClientLevelType.CLIENT);

                    if (app.getClientId().equals(clientHierarchy.getManageClientLevel1()))
                        return Mono.just(ClientLevelType.CUSTOMER);

                    return Mono.just(ClientLevelType.CONSUMER);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.getClientLevelType(ULong,ULong)"));
    }

    public Mono<Client> createForRegistration(Client client, ULong loggedInFromClientId) {
        return this.readInternal(loggedInFromClientId)
                .flatMap(parent -> super.create(client.setLevelType(Client.getChildClientLevelType(parent.getLevelType()))));
    }

    public Mono<Client> getActiveClient(ULong clientId) {
        return this.isClientActive(clientId)
                .flatMap(isActive -> Boolean.TRUE.equals(isActive)
                        ? this.getClientInfoById(clientId)
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.addClientPackagesAfterRegistration"))
                .switchIfEmpty(securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.INACTIVE_CLIENT));
    }

    public Mono<Boolean> isClientActive(ULong clientId) {
        return this.clientHierarchyService.getClientHierarchyIds(clientId).collectList()
                .flatMap(clientHie -> this.dao.isClientActive(clientHie));
    }

    public Mono<Boolean> addClientRegistrationObjects(ULong appId, ULong appClientId, ULong urlClientId,
                                                      Client client) {

        return FlatMapUtil.flatMapMono(
                () -> this.getClientLevelType(client.getId(), appId),

                levelType -> this.appRegistrationDAO.getProfileRestrictionIdsForRegistration(appId, appClientId,
                        urlClientId,
                        client.getTypeCode(), levelType, client.getBusinessType()),

                (levelType, profileIds) -> {

                    if (profileIds == null || profileIds.isEmpty())
                        return Mono.just(true);

                    return this.dao.createProfileRestrictions(client.getId(), profileIds);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.addClientRegistrationObjects"));
    }
}
