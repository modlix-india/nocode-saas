package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.math.BigInteger;
import java.net.URI;
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
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.ObjectUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.Package;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.ClientRegistrationResponse;
import com.fincity.security.util.PasswordUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ClientService
		extends AbstractSecurityUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

	public static final String CACHE_CLIENT_URL_LIST = "list";
	public static final String CACHE_NAME_CLIENT_URL = "clientUrl";
	public static final String CACHE_NAME_CLIENT_URI = "uri";

	private static final String CACHE_NAME_CLIENT_RELATION = "clientRelation";
	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";
	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";
	private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";
	private static final String CACHE_NAME_CLIENT_INFO = "clientInfoById";
	private static final String CACHE_NAME_MANAGED_CLIENT_INFO = "managedClientInfoById";
	private static final String CACHE_NAME_CLIENT_ID = "clientId";

	private static final String ASSIGNED_PACKAGE = "Package is assigned to Client ";

	private static final String UNASSIGNED_PACKAGE = "Package is removed from Client ";

	private static final int VALIDITY_MINUTES = 30;

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
	@Lazy
	private TokenService tokenService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private EventCreationService ecService;

	@Autowired
	@Lazy
	private AuthenticationService authenticationService;

	@Value("${jwt.token.rememberme.expiry}")
	private Integer remembermeExpiryInMinutes;

	@Value("${jwt.token.default.expiry}")
	private Integer defaultExpiryInMinutes;

	@Value("${security.subdomain.endings}")
	private String[] subDomainURLEndings;

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
				.flatMap(app -> this.getClientInfoById(app.getClientId()
						.toBigInteger())
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
		return this.dao.isBeingManagedBy(managingClientId, clientId);
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong id) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_CLIENT_PWD_POLICY,
				() -> this.dao.getClientPasswordPolicy(id), id);
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

	// For creating user.
	public Mono<Boolean> validatePasswordPolicy(ULong clientId, String password) { // NOSONAR

		return this.dao.getClientPasswordPolicy(clientId)
				.map(e -> {// NOSONAR
							// Need to check the password policy
					return true;
				})
				.switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	// For existing user.
	public Mono<Boolean> validatePasswordPolicy(ULong clientId, ULong userId, String password) { // NOSONAR

		return this.dao.getClientPasswordPolicy(clientId)
				.map(e -> { // NOSONAR
							// Need to check the password policy
					return true;
				})
				.switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	public Mono<Client> getClientInfoById(BigInteger id) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_INFO, () -> this.read(ULong.valueOf(id)), id);
	}

	public Mono<Client> getManagedClientOfClientById(ULong clientId) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_MANAGED_CLIENT_INFO,
				() -> this.dao.getManagingClientId(clientId)
						.flatMap(e -> this.getClientInfoById(e.toBigInteger())),
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

					return flatMapMono(

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

				}

				);

	}

	private Mono<Boolean> checkClientAndPackageManaged(ULong loggedInClientId, ULong clientId, ULong packageClientId) {

		return flatMapMono(

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

					return flatMapMono(

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

	public Mono<ClientRegistrationResponse> register(
			ClientRegistrationRequest registrationRequest, ServerHttpRequest request,
			ServerHttpResponse response) {

		Mono<ContextAuthentication> checkEmailExistsInIndividualClients = FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {
					if (registrationRequest.isBusinessClient())
						return Mono.just(ca);

					return this.userService
							.checkUserExists(ca.getUrlAppCode(), ca.getUrlClientCode(), registrationRequest)
							.flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? null : ca));
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.register"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.CONFLICT, msg),
						SecurityMessageResourceService.USER_ALREADY_EXISTS, registrationRequest.getEmailId()));

		Mono<ClientRegistrationResponse> mono = FlatMapUtil.flatMapMono(

				() -> checkEmailExistsInIndividualClients,

				ca -> this.appService.getProperties(ULong.valueOf(ca.getLoggedInFromClientId()), null,
						ca.getUrlAppCode(), AppService.APP_PROP_REG_TYPE)
						.map(e -> e.isEmpty() ? "" : e.get(0).getValue()),

				(ca, prop) -> this.registerClient(registrationRequest, ca, prop),

				(ca, prop, client) -> this.dao.addManageRecord(ca.getUrlClientCode(), client.getId()),

				(ca, prop, client, manageId) -> this.dao.addDefaultPackages(client.getId(), ca.getUrlClientCode(),
						ca.getUrlAppCode()),

				(ca, prop, client, manageId, added) -> registerUser(registrationRequest, client, prop),

				(ca, prop, client, manageId, added, userTuple) -> this.getClientBy(ca.getUrlClientCode())
						.map(Client::getId),

				(ca, prop, client, manageId, added, userTuple, loggedInClientCode) -> {

					if (!StringUtil.safeIsBlank(registrationRequest.getPassword())) {

						return this.userService.makeOneTimeToken(request, ca, userTuple.getT1(), loggedInClientCode)
								.map(TokenObject::getToken);
					}

					return Mono.just("");
				},

				(ca, prop, client, manageId, added, userTuple, loggedInClientCode, token) -> ecService
						.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
								.setClientCode(ca.getUrlClientCode())
								.setEventName(EventNames.CLIENT_REGISTERED)
								.setData(Map.of("client", client)))
						.flatMap(e -> ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
								.setClientCode(ca.getUrlClientCode())
								.setEventName(EventNames.USER_REGISTERED)
								.setData(Map.of("client", client, "user", userTuple.getT1(), "token", token,
										"passwordUsed", userTuple.getT2()))))
						.flatMap(e -> {
							if (!AppService.APP_PROP_REG_TYPE_NO_VERIFICATION
									.equals(prop))
								return Mono.just(new ClientRegistrationResponse(true, null));

							return this.authenticationService
									.authenticate(new AuthenticationRequest()
											.setUserName(CommonsUtil.nonNullValue(registrationRequest.getUserName(),
													registrationRequest.getEmailId()))
											.setPassword(registrationRequest.getPassword()), request, response)
									.map(x -> new ClientRegistrationResponse(true, x));
						})

		);
		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.register"));
	}

	private Mono<Tuple2<User, String>> registerUser(ClientRegistrationRequest request, Client client, String regType) {
		User user = new User();
		user.setClientId(client.getId());
		user.setEmailId(request.getEmailId());
		user.setFirstName(request.getFirstName());
		user.setLastName(request.getLastName());
		user.setLocaleCode(request.getLocaleCode());
		user.setUserName(request.getUserName());

		String password = "";
		if (regType.contains(AppService.APP_PROP_REG_TYPE_EMAIL_PASSWORD)) {
			password = PasswordUtil.generatePassword(8);
			user.setPassword(password);
			user.setStatusCode(SecurityUserStatusCode.ACTIVE);
		} else if (StringUtil.safeIsBlank(request.getPassword())) {

		} else if (regType.contains(AppService.APP_PROP_REG_TYPE_EMAIL_VERIFY)) {
			user.setPassword(request.getPassword());
			user.setStatusCode(SecurityUserStatusCode.INACTIVE);
		} else if (regType.contains(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION)) {
			user.setPassword(request.getPassword());
			user.setStatusCode(SecurityUserStatusCode.ACTIVE);
		}

		final String finPassword = password;

		return this.userService.createForRegistration(user)
				.map(e -> Tuples.of(e, finPassword));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_READ') and hasAuthority('Authorities.Package_READ')")
	public Mono<List<Package>> fetchPackages(ULong clientId) {

		return flatMapMono(

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

	private Mono<Client> registerClient(ClientRegistrationRequest request, ContextAuthentication ca, String regType) {

		if ("".equals(regType)) {
			return this.securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.NO_REGISTRATION_AVAILABLE);
		}

		if (ca.isAuthenticated())
			return this.securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Signout to register");

		Client client = new Client();
		client.setName(
				StringUtil.safeIsBlank(request.getClientName())
						? (StringUtil.safeValueOf(request.getFirstName(), "")
								+ StringUtil.safeValueOf(request.getLastName(), ""))
						: request.getClientName());
		client.setTypeCode(request.isBusinessClient() ? "BUS" : "INDV");
		client.setLocaleCode(request.getLocaleCode());
		client.setTokenValidityMinutes(VALIDITY_MINUTES);

		if (StringUtil.safeIsBlank(client.getName()))
			return this.securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Client name cannot be blank");

		return flatMapMono(

				() -> this.dao.getValidClientCode(client.getName())
						.map(client::setCode),

				super::create,

				(c, clnt) -> this.appService.addClientAccessAfterRegistration(ca.getUrlAppCode(), clnt.getId(), false),

				(c, clnt, created) -> Mono.just(clnt))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.registerClient"));

	}
}
