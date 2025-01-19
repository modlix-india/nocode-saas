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
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Package;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class ClientService
		extends AbstractSecurityUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

	public static final String CACHE_CLIENT_URL_LIST = "list";
	public static final String CACHE_NAME_CLIENT_URL = "clientUrl";
	public static final String CACHE_NAME_CLIENT_URI = "uri";
	public static final String CC = "clientCode";

	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";
	private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";
	private static final String CACHE_NAME_CLIENT_INFO = "clientInfoById";
	private static final String CACHE_NAME_MANAGED_CLIENT_INFO = "managedClientInfoById";
	private static final String CACHE_NAME_CLIENT_ID = "clientId";

	private static final String ASSIGNED_PACKAGE = "Package is assigned to Client ";

	private static final String UNASSIGNED_PACKAGE = "Package is removed from Client ";

	@Autowired
	private CacheService cacheService;

	@Autowired
	@Lazy
	private PackageService packageService;

	@Autowired
	@Lazy
	private AppService appService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private AppRegistrationDAO appRegistrationDAO;

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

	public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_TYPE, () -> this.dao.getClientTypeNCode(id), id);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
	@Override
	public Mono<Client> create(Client entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> super.create(entity).map(e -> {

					if (!ca.isSystemClient())
						this.clientHierarchyService.create(ULongUtil.valueOf(ca.getUser().getClientId()), e.getId())
								.subscribe();

					return e;
				}));
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
		return super.update(entity).flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_INFO, entity.getId())
				.flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, entity.getId()))
				.map(x -> e));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Client> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields).flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_INFO, e.getId())
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
				.flatMap(e -> this.cacheService.evict(CACHE_NAME_CLIENT_INFO, id)
						.flatMap(y -> this.cacheService.evict(CACHE_NAME_CLIENT_ID, id)))
				.map(e -> 1);
	}

	@Override
	protected Mono<Client> updatableEntity(Client entity) {
		return Mono.just(entity);
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return Mono.just(fields);
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
		return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_INFO, () -> this.read(ULong.valueOf(id)), id);
	}

	public Mono<Client> getClientInfoById(ULong id) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_INFO, () -> this.read(id), id);
	}

	public Mono<Client> getManagedClientOfClientById(ULong clientId) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_MANAGED_CLIENT_INFO,
				() -> this.clientHierarchyService.getManagingClient(clientId, ClientHierarchy.Level.ZERO)
						.flatMap(this::getClientInfoById),
				clientId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> assignPackageToClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId).flatMap(result -> {
			if (Boolean.TRUE.equals(result))
				return Mono.just(Boolean.TRUE);

			return FlatMapUtil.flatMapMono(

					SecurityContextUtil::getUsersContextAuthentication,

					ca -> this.dao.getPackage(packageId),

					(ca, packageRecord) -> BooleanUtil.safeValueOfWithEmpty(packageRecord.isBase()),

					(ca, packageRecord, basePackage) ->

					ca.isSystemClient() ? Mono.just(Boolean.TRUE)
							: checkClientAndPackageManaged(ULong.valueOf(ca.getUser().getClientId()), clientId,
									packageRecord.getClientId()),

					(ca, packageRecord, basePackage, isManaged) ->

					this.dao.addPackageToClient(clientId, packageId)
							.map(e -> {
								if (Boolean.TRUE.equals(e))
									super.assignLog(clientId, ASSIGNED_PACKAGE + packageId);

								return e;
							})

			).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.assignPackageToClient"))
					.switchIfEmpty(securityMessageResourceService.throwMessage(
							msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
							SecurityMessageResourceService.ASSIGN_PACKAGE_ERROR, packageId, clientId));

		});
	}

	public Mono<Boolean> hasPackageAccess(ULong clientId, ULong packageId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getPackage(packageId),

				(ca, pack) -> {

					if (pack.isBase())
						return Mono.just(Boolean.TRUE);

					if (CommonsUtil.safeEquals(clientId, pack.getClientId()))
						return Mono.just(Boolean.TRUE);

					return this.dao.checkPackageAssignedForClient(clientId, packageId)
							.flatMap(e -> Boolean.TRUE.equals(e) ? Mono.just(Boolean.TRUE)
									: this.isBeingManagedBy(clientId, pack.getClientId()));
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.hasPackageAccess"));
	}

	private Mono<Boolean> checkClientAndPackageManaged(ULong loggedInClientId, ULong clientId, ULong packageClientId) {

		return FlatMapUtil.flatMapMono(

				() -> this.isBeingManagedBy(loggedInClientId, clientId)
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				clientManaged -> this.isBeingManagedBy(loggedInClientId, packageClientId)
						.flatMap(BooleanUtil::safeValueOfWithEmpty))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.checkClientAndPackageManaged"));
	}

	public Mono<Boolean> checkPermissionExistsOrCreatedForClient(ULong clientId, ULong permissionId) {
		return this.dao.checkPermissionExistsOrCreatedForClient(clientId, permissionId);
	}

	public Mono<Client> getClientBy(String clientCode) {
		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_CODE, () -> this.dao.getClientBy(clientCode), clientCode);
	}

	public Mono<ULong> getClientId(String clientCode) {
		return this.getClientBy(clientCode).map(Client::getId);
	}

	public Mono<Boolean> checkRoleExistsOrCreatedForClient(ULong clientId, ULong roleId) {
		return this.dao.checkRoleExistsOrCreatedForClient(clientId, roleId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> removePackageFromClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId).flatMap(result -> {
			if (Boolean.FALSE.equals(result))
				return securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
						AbstractMessageService.OBJECT_NOT_FOUND, clientId, packageId);

			return FlatMapUtil.flatMapMono(

					SecurityContextUtil::getUsersContextAuthentication,

					ca -> ca.isSystemClient() ? Mono.just(true)
							: this.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
									.flatMap(BooleanUtil::safeValueOfWithEmpty),

					(ca, sysOrManaged) -> this.packageService.getRolesFromPackage(packageId),

					(ca, sysOrManaged, roles) -> this.packageService.omitRolesFromBasePackage(roles),

					(ca, sysOrManaged, roles, finalRoles) -> this.dao.findAndRemoveRolesFromUsers(finalRoles,
							packageId),

					(ca, sysOrManaged, roles, finalRoles, rolesRemoved) -> this.packageService
							.getPermissionsFromPackage(packageId, finalRoles),

					(ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions) -> this.packageService
							.omitPermissionsFromBasePackage(permissions),

					(ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions,
							finalPermissions) -> this.dao.findAndRemovePermissionsFromUsers(finalPermissions,
									packageId),

					(ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions, finalPermissions,
							rolesPermissionsRemoved) ->

					this.dao.removePackage(clientId, packageId)
							.map(e -> {
								if (Boolean.TRUE.equals(e))
									super.unAssignLog(packageId, UNASSIGNED_PACKAGE + clientId);

								return e;
							})

			).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.removePackageFromClient"));
		})
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.REMOVE_PACKAGE_ERR0R, packageId, clientId));

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

	@PreAuthorize("hasAuthority('Authorities.Client_READ') and hasAuthority('Authorities.Package_READ')")
	public Mono<List<Package>> fetchPackages(ULong clientId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)
						: this.isBeingManagedBy(ULongUtil.valueOf(ca.getLoggedInFromClientId()), clientId)
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, sysOrManaged) -> this.dao.getPackagesAvailableForClient(clientId))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.fetchPackages"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FETCH_PACKAGE_ERROR, clientId));

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

	public Mono<Client> createForRegistration(Client client) {
		return super.create(client);
	}

	public Mono<Boolean> addClientPackagesAfterRegistration(ULong appId, ULong appClientId, ULong urlClientId,
			Client client) {

		return FlatMapUtil.flatMapMono(

				() -> this.getClientLevelType(client.getId(), appId),

				levelType -> this.appRegistrationDAO.getPackageIdsForRegistration(appId, appClientId, urlClientId,
						client.getTypeCode(), levelType, client.getBusinessType()),

				(levelType, packageIds) -> Flux.fromIterable(packageIds)
						.flatMap(e -> this.dao.addPackageToClient(client.getId(), e))
						.collectList(),

				(levelType, packageIds, results) -> Mono.just(true)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.addClientPackagesAfterRegistration"));
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
}
