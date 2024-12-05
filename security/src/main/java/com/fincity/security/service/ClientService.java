package com.fincity.security.service;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.CodeAccessDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.CodeAccess;
import com.fincity.security.dto.Package;
import com.fincity.security.dto.UserClient;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
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

	private static final String CACHE_NAME_CLIENT_RELATION = "clientRelation";
	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";
	private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";
	private static final String CACHE_NAME_CLIENT_MANAGED = "clientManaged";
	private static final String CACHE_NAME_CLIENT_INFO = "clientInfoById";
	private static final String CACHE_NAME_MANAGED_CLIENT_INFO = "managedClientInfoById";
	private static final String CACHE_NAME_CLIENT_ID = "clientId";

	private static final String CLIENT_ID = "clientId";

	private static final String ASSIGNED_PACKAGE = "Package is assigned to Client ";

	private static final String UNASSIGNED_PACKAGE = "Package is removed from Client ";

	@Autowired
	private CacheService cacheService;

	@Autowired
	@Lazy
	private PackageService packageService;

	@Autowired
	@Lazy
	private UserService userService;

	@Autowired
	@Lazy
	private AppService appService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private EventCreationService ecService;

	@Autowired
	private CodeAccessDAO codeAccessDAO;

	@Autowired
	private AppRegistrationDAO appRegistrationDAO;

	@Autowired
	private ClientPasswordPolicyService clientPasswordPolicyService;

	@Autowired
	private ClientPinPolicyService clientPinPolicyService;

	private final EnumMap<AuthenticationPasswordType, IPolicyService<? extends AbstractPolicy>> policyServices = new EnumMap<>(
			AuthenticationPasswordType.class);

	@Value("${jwt.token.rememberme.expiry}")
	private Integer remembermeExpiryInMinutes;

	@Value("${jwt.token.default.expiry}")
	private Integer defaultExpiryInMinutes;

	@Value("${security.subdomain.endings}")
	private String[] subDomainURLEndings;

	@PostConstruct
	public void init() {
		this.policyServices.putAll(Map.of(
				clientPasswordPolicyService.getAuthenticationPasswordType(), clientPasswordPolicyService,
				clientPinPolicyService.getAuthenticationPasswordType(), clientPinPolicyService));
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
				.defaultIfEmpty(ULong.valueOf(1l))
				.flatMap(this::readInternal);
	}

	public Mono<List<Client>> getClientsBy(List<ULong> ids) {
		return this.dao.getClientsBy(ids);
	}

	public Mono<ClientUrlPattern> getClientPattern(String uriScheme, String uriHost, String uriPort) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_URI, () -> {

			String finHost = uriHost;

			return this.readAllAsClientURLPattern()
					.flatMapIterable(e -> e)
					.filter(e -> e.isValidClientURLPattern(finHost, uriPort))
					.take(1)
					.collectList()
					.flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e.get(0)))
					.switchIfEmpty(Mono.defer(() -> getClientPatternBySubdomain(uriHost)));

		}, uriScheme, uriHost, ":", uriPort);
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

	public Mono<Set<ULong>> getPotentialClientList(ServerHttpRequest request) {

		return this.getClientBy(request)
				.map(Client::getId)
				.flatMap(this::getPotentialClientList);
	}

	public Mono<Set<ULong>> getPotentialClientList(ULong id) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_RELATION, () -> this.dao.findManagedClientList(id), id);
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {
		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_MANAGED,
				() -> this.dao.isBeingManagedBy(managingClientId, clientId), managingClientId, "-", clientId);
	}

	@SuppressWarnings("unchecked")
	public <T extends AbstractPolicy> Mono<T> getClientAppPolicy(ULong clientId, ULong appId,
			AuthenticationPasswordType passType) {

		IPolicyService<T> policyService = (IPolicyService<T>) policyServices.get(passType);

		return cacheService.cacheEmptyValueOrGet(policyService.getPolicyCacheName(),
				() -> policyService.getClientAppPolicy(clientId, appId), clientId, appId);
	}

	public <T extends AbstractPolicy> Mono<T> getClientAppPolicy(ULong clientId, String appCode,
			AuthenticationPasswordType passType) {

		return FlatMapUtil.flatMapMono(
				() -> appService.getAppByCode(appCode),
				app -> getClientAppPolicy(clientId, app.getId(), passType));
	}

	public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_TYPE, () -> this.dao.getClientTypeNCode(id), id);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
	@Override
	public Mono<Client> create(Client entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> super.create(entity).map(e -> {

					if (!ca.isSystemClient()) {

						ULong mClientId = ULongUtil.valueOf(ca.getUser()
								.getClientId());
						this.addManageRecord(mClientId, e.getId())
								.flatMap(cacheService.evictFunction(CACHE_NAME_CLIENT_RELATION, e.getId()))
								.subscribe();
					}

					return e;
				}));
	}

	private Mono<Integer> addManageRecord(ULong manageClientId, ULong id) {

		return this.dao.addManageRecord(manageClientId, id);
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

	public Mono<Boolean> validatePasswordPolicy(ULong clientId, ULong appId, AuthenticationPasswordType passType,
			String password) {
		return policyServices.get(passType).checkAllConditions(clientId, appId, password)
				.switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	public Mono<Boolean> validatePasswordPolicy(ULong clientId, String appCode, AuthenticationPasswordType passType,
			String password) {
		return FlatMapUtil.flatMapMono(
				() -> appService.getAppByCode(appCode),
				app -> validatePasswordPolicy(clientId, app.getId(), passType, password))
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
				() -> this.dao.getManagingClientId(clientId)
						.flatMap(this::getClientInfoById),
				clientId);
	}

	public Mono<Boolean> checkClientHasPackage(ULong clientId, ULong packageId) {
		return this.dao.checkPackageAssignedForClient(clientId, packageId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> assignPackageToClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId)
				.flatMap(result -> {
					if (result.booleanValue())
						return Mono.just(true);

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.dao.getPackage(packageId),

							(ca, packageRecord) -> BooleanUtil.safeValueOfWithEmpty(packageRecord.isBase()),

							(ca, packageRecord, basePackage) ->

							ca.isSystemClient() ? Mono.just(true)
									: checkClientAndPackageManaged(ULong.valueOf(ca.getUser()
											.getClientId()), clientId, packageRecord.getClientId()),

							(ca, packageRecord, basePackage, isManaged) ->

							this.dao.addPackageToClient(clientId, packageId)
									.map(e -> {
										if (e.booleanValue())
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
						return Mono.just(true);

					if (CommonsUtil.safeEquals(clientId, pack.getClientId()))
						return Mono.just(true);

					return this.dao.checkPackageAssignedForClient(clientId, packageId)
							.flatMap(e -> e.booleanValue() ? Mono.just(true)
									: this.isBeingManagedBy(clientId, pack.getClientId()));
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.hasPackageAccess"));
	}

	public Mono<Boolean> hasRoleAccess(ULong clientId, ULong roleId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getRole(roleId),

				(ca, role) -> {

					if (CommonsUtil.safeEquals(clientId, role.getClientId()))
						return Mono.just(true);

					return this.dao.checkRoleAssignedForClient(clientId, roleId)
							.flatMap(e -> e.booleanValue() ? Mono.just(true)
									: this.isBeingManagedBy(clientId, role.getClientId()));
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.hasRoleAccess"));
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

	public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {
		return this.dao.isBeingManagedBy(managingClientCode, clientCode);
	}

	public Mono<Boolean> isUserBeingManaged(ULong userId, String clientCode) {

		return this.dao.isUserBeingManaged(userId, clientCode);
	}

	public Mono<Client> getClientBy(String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_CODE, () -> this.dao.getClientBy(clientCode), clientCode);
	}

	public Mono<Boolean> checkRoleExistsOrCreatedForClient(ULong clientId, ULong roleId) {
		return this.dao.checkRoleExistsOrCreatedForClient(clientId, roleId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> removePackageFromClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId)
				.flatMap(result -> {
					if (!result.booleanValue())
						return securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								SecurityMessageResourceService.OBJECT_NOT_FOUND, clientId, packageId);

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> ca.isSystemClient() ? Mono.just(true)
									: this.isBeingManagedBy(ULong.valueOf(ca.getUser()
											.getClientId()), clientId)
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
										if (e.booleanValue())
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

				ca -> Mono.just(CommonsUtil.nonNullValue(clientId, ULong.valueOf(ca.getUser()
						.getClientId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(true)
						: this.dao.isBeingManagedBy(ULong.valueOf(ca.getUser()
								.getClientId()), id),

				(ca, id, sysOrManaged) -> sysOrManaged.booleanValue() ? this.dao.makeClientActiveIfInActive(clientId)
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

				ca -> Mono.just(CommonsUtil.nonNullValue(clientId, ULong.valueOf(ca.getUser()
						.getClientId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(true)
						: this.dao.isBeingManagedBy(ULong.valueOf(ca.getUser()
								.getClientId()), id),

				(ca, id, sysOrManaged) -> sysOrManaged.booleanValue() ? this.dao.makeClientInActive(clientId)
						: Mono.empty())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.makeClientIfInActive"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "client"));
	}

	public Mono<Page<CodeAccess>> fetchCodesBasedOnClient(Pageable page, String clientCode, String emailId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(ca.isSystemClient() && !StringUtil.safeIsBlank(clientCode) ? clientCode
						: ca.getLoggedInFromClientCode()),

				(ca, finClientCode) -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(ca, finClientCode, app) -> this.getClientBy(finClientCode),

				(ca, finClientCode, app, client) -> {

					List<AbstractCondition> conditions = new ArrayList<>(
							List.of(FilterCondition.make("appId", app.getId()),
									FilterCondition.make(CLIENT_ID, client.getId())));

					if (!StringUtil.safeIsBlank(emailId))
						conditions.add(FilterCondition.make("emaiId", emailId)
								.setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL));

					AbstractCondition condition = new ComplexCondition().setConditions(conditions)
							.setOperator(ComplexConditionOperator.AND);

					return this.codeAccessDAO.readPageFilter(page, condition);

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.fetchCodesWithApp"));

	}

	public Mono<Boolean> tiggerMailOnRequest(ULong accessCodeId, ServerHttpRequest request) {

		String host = request.getHeaders().getFirst("X-Forwarded-Host");
		String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
		String port = request.getHeaders().getFirst("X-Forwarded-Port");

		String urlPrefix = (scheme != null && scheme.contains("https")) ? "https://" + host
				: "http://" + host + ":" + port;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.codeAccessDAO.readById(accessCodeId),

				(ca, accessCode) -> (accessCode.getClientId()
						.equals(ULong.valueOf(ca.getUser()
								.getClientId()))
						|| ca.isSystemClient()) ? Mono.just(true) : Mono.empty(),

				(ca, accessCode, hasAccess) -> ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
						.setClientCode(ca.getClientCode())
						.setEventName(EventNames.USER_CODE_GENERATION)
						.setData(Map.of("emailId", accessCode.getEmailId(), "accessCode", accessCode.getCode(),
								"urlPrefix", urlPrefix))))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.tiggerMailOnRequest"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
						SecurityMessageResourceService.MAIL_CANNOT_BE_TRIGGERED));
	}

	public Mono<Boolean> generateCodeAndTriggerMail(String emailId, ServerHttpRequest request) { // NOSONAR

		String host = request.getHeaders().getFirst("X-Forwarded-Host");
		String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
		String port = request.getHeaders().getFirst("X-Forwarded-Port");

		String urlPrefix = (scheme != null && scheme.contains("https")) ? "https://" + host
				: "http://" + host + ":" + port;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(ca, app) -> this.getClientBy(ca.getUrlClientCode()),

				(ca, app, client) -> this.userService.findUserClients(new AuthenticationRequest().setUserName(emailId)
						.setIdentifierType(AuthenticationIdentifierType.EMAIL_ID), ca.getUrlAppCode(),
						ca.getUrlClientCode()).flatMap(
								e -> this.dao.getManagingClientIds(e.stream()
										.map(UserClient::getClient)
										.map(Client::getId)
										.toList())),

				(ca, app, client, userClients) -> {

					boolean hasUser = userClients.stream()
							.anyMatch(e -> e.equals(client.getId()));

					return Mono.justOrEmpty(!hasUser ? true : null);
				},

				(ca, app, client, userClients, exists) -> this.appService
						.getProperties(client.getId(), app.getId(), null, AppService.APP_PROP_REG_TYPE)
						.flatMap(e -> {

							if (e.isEmpty() || !e.containsKey(client.getId()))
								return Mono.empty();

							AppProperty prop = e.get(client.getId())
									.get(AppService.APP_PROP_REG_TYPE);

							if (prop == null)
								return Mono.empty();

							if (AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE.equals(prop.getValue())
									|| AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE_LOGIN_IMMEDIATE
											.equals(prop.getValue())
									|| AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST.equals(prop.getValue())
									|| AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST_LOGIN_IMMEDIATE
											.equals(prop.getValue()))

								return Mono.just(prop.getValue());

							return Mono.empty();

						}),

				(ca, app, client, userClients, exists,
						prop) -> this.codeAccessDAO.create(new CodeAccess().setAppId(app.getId())
								.setClientId(client.getId())
								.setEmailId(emailId)),

				(ca, app, client, userClients, exists, prop, x) -> {

					if (AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE.equals(prop)
							|| AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE_LOGIN_IMMEDIATE.equals(prop))
						return ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
								.setClientCode(client.getCode())
								.setEventName(EventNames.USER_CODE_GENERATION)
								.setData(Map.of("emailId", x.getEmailId(), "accessCode", x.getCode(), "urlPrefix",
										urlPrefix)));

					return Mono.just(true);

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.generateCodeAndTriggerMail"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
						SecurityMessageResourceService.USER_ALREADY_CREATED));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_READ') and hasAuthority('Authorities.Package_READ')")
	public Mono<List<Package>> fetchPackages(ULong clientId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)
						: this.isBeingManagedBy(ULongUtil.valueOf(ca.getLoggedInFromClientId()), clientId)
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, sysOrManaged) -> this.dao.getPackagesAvailableForClient(clientId)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.fetchPackages"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FETCH_PACKAGE_ERROR, clientId));

	}

	public Mono<ULong> getSystemClientId() {
		return this.cacheService.cacheValueOrGet("CACHE_SYSTEM_CLIENT_ID", () -> this.dao.getSystemClientId(),
				"SYSTEM");
	}

	public Mono<ClientLevelType> getClientLevelType(ULong clientId, String appCode) {

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(appCode),

				app -> this.getClientLevelType(clientId, app.getId()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.getClientLevelType(ULong,String)"));
	}

	public static record LevelTypeReturnRecord(ClientLevelType type, Client client) {
		public LevelTypeReturnRecord(ClientLevelType type) {
			this(type, null);
		}

		public LevelTypeReturnRecord(Client client) {
			this(null, client);
		}
	}

	// Only four levels of the client type is fixed and any further will lead to
	// confusion.
	public Mono<ClientLevelType> getClientLevelType(ULong clientId, ULong appId) {

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppById(appId),

				app -> this.getClientInfoById(app.getClientId()),

				(app, appClient) -> {
					if (appClient.getId()
							.equals(clientId))
						return Mono.just(new LevelTypeReturnRecord(ClientLevelType.OWNER));

					return this.getManagedClientOfClientById(clientId).map(LevelTypeReturnRecord::new);
				},

				(app, appClient, first) -> {

					if (first.type() != null)
						return Mono.just(first);

					if (first.client()
							.getId()
							.equals(appClient.getId()))
						return Mono.just(new LevelTypeReturnRecord(ClientLevelType.CLIENT));

					return this.getManagedClientOfClientById(first.client.getId())
							.map(LevelTypeReturnRecord::new);
				},

				(app, appClient, first, second) -> {

					if (second.type() != null)
						return Mono.just(second);

					if (second.client()
							.getId()
							.equals(appClient.getId()))
						return Mono.just(new LevelTypeReturnRecord(ClientLevelType.CUSTOMER));

					return this.getManagedClientOfClientById(second.client.getId())
							.map(LevelTypeReturnRecord::new);
				},

				(app, appClient, first, second, third) -> {

					if (third.type() != null)
						return Mono.just(third);

					if (third.client()
							.getId()
							.equals(appClient.getId()))
						return Mono.just(new LevelTypeReturnRecord(ClientLevelType.CONSUMER));

					return Mono.empty();
				})
				.map(LevelTypeReturnRecord::type)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.getClientLevelType(ULong,ULong)"));
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

	public Mono<Boolean> isClientActive(ULong clientId) {

		return FlatMapUtil.flatMapMono(

				() -> this.getClientInfoById(clientId),

				c -> c.getStatusCode() != SecurityClientStatusCode.ACTIVE ? Mono.empty() : Mono.just(true),

				(c, active) -> {

					if ("SYS".equals(c.getTypeCode()))
						return Mono.just(true);

					return this.getManagedClientOfClientById(clientId).map(Client::getId).flatMap(this::isClientActive)
							.defaultIfEmpty(true);
				}).defaultIfEmpty(false);
	}
}
