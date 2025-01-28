package com.fincity.security.service;

import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.jwt.JWTUtil;
import com.fincity.saas.commons.security.jwt.JWTUtil.JWTGenerateTokenParameters;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserClient;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.OtpGenerationRequestInternal;
import com.fincity.security.model.RequestUpdatePassword;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class UserService extends AbstractSecurityUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

	private static final String ASSIGNED_PERMISSION = " Permission is assigned to the user ";

	private static final String ASSIGNED_ROLE = " Role is assigned to the user ";

	private static final String UNASSIGNED_PERMISSION = " Permission is removed from the selected user";

	private static final String UNASSIGNED_ROLE = " Role is removed from the selected user";

	private static final int VALIDITY_MINUTES = 30;

	private final ClientService clientService;
	private final AppService appService;
	private final ClientHierarchyService clientHierarchyService;
	private final PasswordEncoder passwordEncoder;
	private final SecurityMessageResourceService securityMessageResourceService;
	private final SoxLogService soxLogService;
	private final OtpService otpService;
	private final TokenService tokenService;
	private final EventCreationService ecService;
	private final AppRegistrationDAO appRegistrationDAO;

	@Value("${jwt.key}")
	private String tokenKey;

	public UserService(ClientService clientService, AppService appService,
			ClientHierarchyService clientHierarchyService, PasswordEncoder passwordEncoder,
			SecurityMessageResourceService securityMessageResourceService, SoxLogService soxLogService,
			OtpService otpService, TokenService tokenService, EventCreationService ecService,
			AppRegistrationDAO appRegistrationDAO) {

		this.clientService = clientService;
		this.appService = appService;
		this.clientHierarchyService = clientHierarchyService;
		this.passwordEncoder = passwordEncoder;
		this.securityMessageResourceService = securityMessageResourceService;
		this.soxLogService = soxLogService;
		this.otpService = otpService;
		this.tokenService = tokenService;
		this.ecService = ecService;
		this.appRegistrationDAO = appRegistrationDAO;
	}

	private <T> Mono<T> forbiddenError(String message, Object... params) {
		return securityMessageResourceService.getMessage(message, params)
				.handle((msg, sink) -> sink.error(new GenericException(HttpStatus.FORBIDDEN, msg)));
	}

	public Mono<Tuple3<Client, Client, User>> findNonDeletedUserNClient(String userName, ULong userId,
			String clientCode, String appCode, AuthenticationIdentifierType authenticationIdentifierType) {
		return this.findUserNClient(userName, userId, clientCode, appCode, authenticationIdentifierType,
				this.getNonDeletedUserStatusCodes());
	}

	public Mono<Tuple3<Client, Client, User>> findUserNClient(String userName, ULong userId, String clientCode,
			String appCode, AuthenticationIdentifierType authenticationIdentifierType,
			SecurityUserStatusCode... userStatusCodes) {

		return FlatMapUtil.flatMapMono(

				() -> this.dao
						.getUsersBy(userName, userId, clientCode, appCode, authenticationIdentifierType,
								userStatusCodes)
						.flatMap(users -> Mono.justOrEmpty(users.size() != 1 ? null : users.getFirst()))
						.flatMap(this.dao::setPermissions),

				user -> this.clientService.getActiveClient(user.getClientId()),

				(user, uClient) -> uClient.getCode().equals(clientCode) ? Mono.just(uClient)
						: this.clientService.getClientBy(clientCode),

				(user, uClient, mClient) -> Mono.just(Tuples.of(mClient, uClient, user)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.findUserNClient"));
	}

	public Mono<Boolean> checkUserAndClient(Tuple3<Client, Client, User> userNClient, String clientCode) {

		if (clientCode == null)
			return Mono.just(Boolean.FALSE);

		return Mono.just(
				ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(userNClient.getT1().getTypeCode()) ||
						clientCode.equals(userNClient.getT1().getCode()) ||
						userNClient.getT1().getId().equals(userNClient.getT2().getId()) ? Boolean.TRUE : Boolean.FALSE);
	}

	public Mono<Boolean> checkUserStatus(User user, SecurityUserStatusCode... userStatusCodes) {

		return Mono.justOrEmpty(user).filter(u -> u != null && userStatusCodes.length > 0)
				.map(User::getStatusCode)
				.map(statusCode -> Arrays.asList(userStatusCodes).contains(statusCode))
				.defaultIfEmpty(Boolean.FALSE);
	}

	public Mono<User> getUserForContext(ULong id) {
		return this.dao.readById(id).flatMap(this.dao::setPermissions);
	}

	public Mono<Short> increaseFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
		return this.dao.increaseFailedAttempt(userId, passwordType);
	}

	public Mono<Boolean> resetFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
		return this.dao.resetFailedAttempt(userId, passwordType);
	}

	public Mono<Short> increaseResendAttempt(ULong userId) {
		return this.dao.increaseResendAttempts(userId);
	}

	public Mono<Boolean> resetResendAttempt(ULong userId) {
		return this.dao.resetResendAttempts(userId);
	}

	public SecurityUserStatusCode[] getNonDeletedUserStatusCodes() {
		return new SecurityUserStatusCode[] { SecurityUserStatusCode.ACTIVE, SecurityUserStatusCode.INACTIVE,
				SecurityUserStatusCode.LOCKED, SecurityUserStatusCode.PASSWORD_EXPIRED };
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
				.map(ContextUser::getId)
				.map(ULong::valueOf);
	}

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.USER;
	}

	@PreAuthorize("hasAuthority('Authorities.User_CREATE')")
	@Override
	public Mono<User> create(User entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (entity.getClientId() == null) {
						entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
						return Mono.just(entity);
					}

					updateUserIdentificationKeys(entity);

					if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
						return Mono.just(entity);

					return clientService
							.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
							.flatMap(e -> Boolean.TRUE.equals(e) ? Mono.just(entity) : Mono.empty());
				},
				(ca, user) -> checkUserIdentificationKeys(entity),

				(ca, user, isValid) -> this.getPasswordEntities(user),

				(ca, user, isValid, pass) -> this.passwordEntitiesPolicyCheck(
						ULongUtil.valueOf(ca.getLoggedInFromClientId()), ca.getUrlAppCode(), user.getId(), pass),

				(ca, user, isValid, pass, passValid) -> this.checkBusinessClientUser(user.getClientId(),
						user.getUserName(), user.getEmailId(), user.getPhoneNumber()),

				(ca, user, isValid, pass, passValid, isAvailable) -> this.dao.create(user),

				(ca, user, isValid, pass, passValid, isAvailable, createdUser) -> {

					this.soxLogService.createLog(createdUser.getId(), CREATE, getSoxObjectName(), "User created");
					return this.setPasswordEntities(createdUser, pass);

				}).switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_CREATE, "User"));
	}

	private void updateUserIdentificationKeys(User entity) {

		if (StringUtil.safeIsBlank(entity.getUserName()))
			entity.setUserName(User.PLACEHOLDER);

		if (StringUtil.safeIsBlank(entity.getEmailId()))
			entity.setEmailId(User.PLACEHOLDER);

		if (StringUtil.safeIsBlank(entity.getPhoneNumber()))
			entity.setPhoneNumber(User.PLACEHOLDER);
	}

	private Mono<Boolean> checkUserIdentificationKeys(User entity) {
		if (entity.checkIdentificationKeys())
			return this.forbiddenError(SecurityMessageResourceService.USER_IDENTIFICATION_NOT_FOUND);

		return Mono.just(Boolean.TRUE);
	}

	private Mono<Map<AuthenticationPasswordType, String>> getPasswordEntities(User user) {

		Map<AuthenticationPasswordType, String> passEntities = new EnumMap<>(AuthenticationPasswordType.class);

		if (!StringUtil.safeIsBlank(user.getPassword())) {
			passEntities.put(AuthenticationPasswordType.PASSWORD, user.getPassword());
			user.setPassword(null);
			user.setPasswordHashed(false);
		}

		if (!StringUtil.safeIsBlank(user.getPin())) {
			passEntities.put(AuthenticationPasswordType.PIN, user.getPin());
			user.setPin(null);
			user.setPinHashed(false);
		}

		return Mono.just(passEntities);
	}

	private Mono<Boolean> passwordEntitiesPolicyCheck(ULong clientId, String urlAppCode, ULong userId,
			Map<AuthenticationPasswordType, String> passEntities) {

		return Flux.fromIterable(passEntities.entrySet())
				.flatMap(passEntry -> this
						.passwordPolicyCheck(clientId, null, urlAppCode, userId, passEntry.getKey(),
								passEntry.getValue())
						.onErrorResume(e -> Mono.just(Boolean.FALSE)))
				.all(result -> result)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.passwordEntitiesPolicyCheck"));
	}

	private Mono<Boolean> passwordPolicyCheck(ULong urlClientId, ULong appId, String appCode, ULong userId,
			AuthenticationPasswordType passwordType, String password) {

		return appId != null
				? this.clientService.validatePasswordPolicy(urlClientId, appId, userId, passwordType, password)
				: this.clientService.validatePasswordPolicy(urlClientId, appCode, userId, passwordType, password);
	}

	private Mono<Boolean> checkBusinessClientUser(ULong clientId, String userName, String emailId, String phoneNumber) {

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientTypeNCode(clientId),

				clientTypeNCode -> clientTypeNCode.getT1().equals("INDV") ? Mono.empty()
						: Mono.just(clientTypeNCode.getT1()),

				(clientTypeNCode, clientType) -> this.dao.checkUserExists(clientId, "BUS", userName, emailId,
						phoneNumber));
	}

	private Mono<User> setPasswordEntities(User user, Map<AuthenticationPasswordType, String> passEntities) {

		return FlatMapUtil.flatMapMono(

				this::getLoggedInUserId,

				loggedInUserId -> Flux.fromIterable(
						passEntities.entrySet())
						.flatMap(passEntry -> this.setPassword(user.getId(), loggedInUserId, passEntry.getValue(),
								passEntry.getKey()))
						.then(Mono.just(user)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.setPasswordEntities"));
	}

	@Override
	public Mono<User> read(ULong id) {

		return super.read(id).flatMap(e -> SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {
					if (id.equals(ULong.valueOf(ca.getUser()
							.getId())))
						return Mono.just(e);

					if (!SecurityContextUtil.hasAuthority("Authorities.User_READ", ca.getAuthorities()))
						return Mono.defer(() -> this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_PERMISSION,
								"User READ"));

					return Mono.just(e);
				}))
				.switchIfEmpty(
						Mono.defer(() -> this.forbiddenError(AbstractMessageService.OBJECT_NOT_FOUND, "User", id)));
	}

	@PreAuthorize("hasAuthority('Authorities.User_READ')")
	@Override
	public Mono<Page<User>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@Override
	public Mono<User> update(ULong key, Map<String, Object> fields) {

		String userName = fields.containsKey("userName") ? fields.get("userName")
				.toString() : null;

		String emailId = fields.containsKey("emailId") ? fields.get("emailId")
				.toString() : null;

		String phoneNumber = fields.containsKey("phoneNumber") ? fields.get("phoneNumber")
				.toString() : null;

		return FlatMapUtil.flatMapMono(

				() -> this.dao.getUserClientId(key),

				clientId -> this.clientService.getClientTypeNCode(clientId).map(Tuple2::getT1),

				(clientId, clientType) -> switch (clientType) {
					case "INDV" -> this.clientHierarchyService.getManagingClient(clientId, ClientHierarchy.Level.ZERO)
							.flatMap(managingClientId -> this.dao.checkUserExistsExclude(managingClientId, userName,
									emailId, phoneNumber, "INDV", key));
					case "BUS" -> this.dao.checkUserExists(clientId, userName, emailId, phoneNumber, null);
					default -> Mono.empty();
				},

				(clientId, clientType, userExists) -> Boolean.TRUE.equals(userExists) ? Mono.empty()
						: super.update(key, fields),

				(clientId, clientType, userExists, updated) -> this.evictTokens(updated.getId())
						.<User>map(evicted -> updated))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
	}

	@Override
	public Mono<User> update(User entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientTypeNCode(entity.getClientId()).map(Tuple2::getT1),

				clientType -> switch (clientType) {
					case "INDV" ->
						this.clientHierarchyService.getManagingClient(entity.getClientId(), ClientHierarchy.Level.ZERO)
								.flatMap(managingClientId -> this.dao.checkUserExistsExclude(managingClientId,
										entity.getUserName(), entity.getEmailId(), entity.getPhoneNumber(), "INDV",
										entity.getId()));
					case "BUS" ->
						this.dao.checkUserExists(entity.getClientId(), entity.getUserName(), entity.getEmailId(),
								entity.getPhoneNumber(), null);
					default -> Mono.empty();
				},

				(clientType, userExists) -> Boolean.TRUE.equals(userExists) ? Mono.empty()
						: super.update(entity),

				(clientType, userExists, updated) -> this.evictTokens(updated.getId())
						.<User>map(evicted -> updated))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
	}

	private Mono<Integer> evictTokens(ULong id) {
		return this.tokenService.evictTokensOfUser(id);
	}

	@Override
	protected Mono<User> updatableEntity(User entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setUserName(entity.getUserName());
					e.setEmailId(entity.getEmailId());
					e.setPhoneNumber(entity.getPhoneNumber());
					e.setFirstName(entity.getFirstName());
					e.setLastName(entity.getLastName());
					e.setMiddleName(entity.getMiddleName());
					e.setLocaleCode(entity.getLocaleCode());
					e.setStatusCode(entity.getStatusCode());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null)
			return Mono.just(new HashMap<>());

		fields.remove("clientId");
		fields.remove("password");
		fields.remove("passwordHashed");
		fields.remove("accountNonExpired");
		fields.remove("accountNonLocked");
		fields.remove("credentialsNonExpired");
		fields.remove("noFailedAttempt");
		fields.remove("statusCode");

		return Mono.just(fields);
	}

	@PreAuthorize("hasAuthority('Authorities.User_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return this.read(id)
				.map(e -> {
					e.setStatusCode(SecurityUserStatusCode.DELETED);
					return e;
				})
				.flatMap(this::update)
				.flatMap(e -> this.evictTokens(e.getId())
						.map(x -> 1));
	}

	public Mono<User> readInternal(ULong id) {
		return this.dao.readInternal(id)
				.flatMap(this.dao::setPermissions);
	}

	@PreAuthorize("hasAuthority('Authorities.User_READ') and hasAuthority('Authorities.Permission_READ')")
	public Mono<List<Permission>> getPermissionsFromGivenUser(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)
						: this.read(userId)
								.flatMap(user -> this.clientService.isBeingManagedBy(
										ULongUtil.valueOf(ca.getLoggedInFromClientId()),
										ULongUtil.valueOf(user.getClientId())))
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, sysOrManaged) ->

				this.dao.fetchPermissionsFromGivenUser(userId)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getPermissionsFromGivenUser"))
				.switchIfEmpty(
						this.forbiddenError(SecurityMessageResourceService.FETCH_PERMISSION_ERROR_FOR_USER, userId));

	}

	@PreAuthorize("hasAuthority('Authorities.Role_READ') and hasAuthority('Authorities.User_READ')")
	public Mono<List<Role>> getRolesFromGivenUser(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)
						: this.read(userId)
								.flatMap(user -> this.clientService.isBeingManagedBy(
										ULongUtil.valueOf(ca.getLoggedInFromClientId()),
										ULongUtil.valueOf(user.getClientId())))
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, sysOrManaged) ->

				this.dao.fetchRolesFromGivenUser(userId)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getRolesFromGivenUser"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FETCH_ROLE_ERROR_FOR_USER, userId));

	}

	public Mono<Boolean> removeFromPermissionList(List<ULong> userList, List<ULong> permissionList) {

		return this.dao.removePermissionListFromUser(userList, permissionList)
				.flatMap(e -> Flux.fromIterable(userList)
						.flatMap(this::evictTokens)
						.collectList()
						.map(x -> e));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> removePermissionFromUser(ULong userId, ULong permissionId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)
						: this.dao.readById(userId)

								.flatMap(user -> this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
										.getClientId()), user.getClientId())),

				(ca, isManaged) ->

				this.dao.removePermissionFromUser(userId, permissionId)
						.map(val -> {
							boolean removed = val > 0;

							if (removed)
								super.unAssignLog(userId, UNASSIGNED_PERMISSION);

							return removed;
						})

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.removePermissionFromUser"))

				.flatMap(e -> this.evictTokens(userId)
						.map(x -> e))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.REMOVE_PERMISSION_ERROR, permissionId,
						userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User')")
	public Mono<Boolean> removeRoleFromUser(ULong userId, ULong roleId) {
		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.readById(userId),

				(ca, user) ->

				ca.isSystemClient() ? Mono.just(true)
						: clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
								.getClientId()), user.getClientId())
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, user, isManaged) ->

				this.dao.removeRoleForUser(userId, roleId)
						.map(val -> {
							boolean removed = val > 0;
							if (removed)
								super.unAssignLog(userId, UNASSIGNED_ROLE);

							return removed;
						})

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.removeRoleFromUser"))

				.flatMap(e -> this.evictTokens(userId)
						.map(x -> e))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> assignPermissionToUser(ULong userId, ULong permissionId) {

		return this.dao.checkPermissionAssignedForUser(userId, permissionId)
				.flatMap(result -> {

					if (Boolean.TRUE.equals(result))
						return Mono.just(result);

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							contextAuth -> this.dao.readById(userId),

							(contextAuth, user) ->

							contextAuth.isSystemClient() ? Mono.just(true) :

									clientService.isBeingManagedBy(ULongUtil.valueOf(contextAuth.getUser()
											.getClientId()), user.getClientId())
											.flatMap(BooleanUtil::safeValueOfWithEmpty),

							(contextAuth, user, sysOrManaged) -> this.clientService
									.checkPermissionExistsOrCreatedForClient(user.getClientId(), permissionId)
									.flatMap(BooleanUtil::safeValueOfWithEmpty),

							(contextAuth, user, sysOrManaged, hasPermission) ->

							this.dao.assignPermissionToUser(userId, permissionId)
									.map(e -> {
										if (Boolean.TRUE.equals(e))
											super.assignLog(userId, ASSIGNED_PERMISSION + permissionId);

										return e;
									})

				).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.assignPermissionToUser"))
							.flatMap(e -> this.evictTokens(userId).map(x -> e))
							.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR,
									permissionId, userId));
				});

	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User')")
	public Mono<Boolean> assignRoleToUser(ULong userId, ULong roleId) {

		return this.dao.checkRoleAssignedForUser(userId, roleId)
				.flatMap(result -> {
					if (Boolean.TRUE.equals(result))
						return Mono.just(result);

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.dao.readById(userId),

							(ca, user) ->

							ca.isSystemClient() ? Mono.just(true) :

									clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
											.getClientId()), user.getClientId())
											.flatMap(BooleanUtil::safeValueOfWithEmpty),

							(ca, user, sysOrManaged) ->

							this.clientService.checkRoleExistsOrCreatedForClient(user.getClientId(), roleId)
									.flatMap(BooleanUtil::safeValueOfWithEmpty),

							(ca, user, sysOrManaged, roleApplicable) ->

							this.dao.addRoleToUser(userId, roleId)
									.map(e -> {
										if (Boolean.TRUE.equals(e))
											super.assignLog(userId, ASSIGNED_ROLE + roleId);

										return e;
									})

				).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.assignRoleToUser"))
							.flatMap(e -> this.evictTokens(userId).map(x -> e))
							.switchIfEmpty(
									this.forbiddenError(SecurityMessageResourceService.ROLE_FORBIDDEN, roleId, userId));
				});
	}

	public Mono<Boolean> updatePassword(ULong userId, RequestUpdatePassword reqPassword) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca.isAuthenticated() ? Mono.just(Boolean.TRUE)
						: this.forbiddenError(SecurityMessageResourceService.LOGIN_REQUIRED),

				(ca, loggedIn) -> updatePassword(ca, userId, reqPassword))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.updateNewPassword : [ " + userId + ", " + reqPassword.getPassType() + "]"))
				.switchIfEmpty(
						this.forbiddenError(SecurityMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, "User", userId));
	}

	public Mono<Boolean> updatePassword(RequestUpdatePassword reqPassword) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca.isAuthenticated() ? Mono.just(Boolean.TRUE)
						: this.forbiddenError(SecurityMessageResourceService.LOGIN_REQUIRED),

				(ca, loggedIn) -> updatePassword(ca, ULongUtil.valueOf(ca.getUser().getId()), reqPassword))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.updateNewPassword : [ loggedInUser, " + reqPassword.getPassType() + "]"))
				.switchIfEmpty(
						this.forbiddenError(SecurityMessageResourceService.OBJECT_NOT_UPDATABLE));
	}

	private Mono<Boolean> updatePassword(ContextAuthentication ca, ULong userId, RequestUpdatePassword reqPassword) {

		return FlatMapUtil.flatMapMono(

				() -> this.dao.readInternal(userId),

				user -> this.isPasswordUpdatable(ca, user, reqPassword, Boolean.TRUE),

				(user, isUpdatable) -> this.checkHierarchy(ca, user),

				(user, isUpdatable, inHierarchy) -> this.updatePasswordInternal(ca, user,
						ULongUtil.valueOf(ca.getUser().getId()), reqPassword.getPassType(),
						reqPassword.getNewPassword(), Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.updateNewPassword : [" + reqPassword.getPassType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	private Mono<Boolean> checkHierarchy(ContextAuthentication ca, User user) {

		ULong loggedInUserClientId = ULong.valueOf(ca.getUser().getClientId());

		if (ca.isSystemClient() || user.getClientId().equals(loggedInUserClientId))
			return Mono.just(Boolean.TRUE);

		return Mono
				.zip(SecurityContextUtil.hasAuthority("Authorities.User_UPDATE"),
						this.clientService.isBeingManagedBy(loggedInUserClientId, user.getClientId()),
						(hasAuthority, isManaged) -> hasAuthority && isManaged)
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.HIERARCHY_ERROR)).log();
	}

	public Mono<Boolean> generateOtpResetPassword(AuthenticationRequest authRequest, ServerHttpRequest request) {

		OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

					return this.findNonDeletedUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(),
							authRequest.setIdentifierType().getIdentifierType());
				},

				(ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, userTup, userCheck) -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(ca, userTup, userCheck, app) -> Mono.just(
						new OtpGenerationRequestInternal()
								.setClientOption(userTup.getT1())
								.setAppOption(app)
								.setWithUserOption(userTup.getT3())
								.setIpAddress(request.getRemoteAddress())
								.setResend(authRequest.isResend())
								.setPurpose(purpose)),

				(ca, userTup, userCheck, app, targetReq) -> this.otpService.generateOtpInternal(targetReq))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.generateOtpResetPassword : [" + authRequest.getInputPassType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	public Mono<Boolean> verifyOtpResetPassword(AuthenticationRequest authRequest) {

		OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

					return this.findNonDeletedUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(),
							authRequest.setIdentifierType().getIdentifierType());
				},

				(ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, userTup, userCheck) -> this.otpService
						.verifyOtpInternal(ca.getUrlAppCode(), userTup.getT3(), purpose, authRequest.getOtp())
						.filter(otpVerified -> otpVerified).map(otpVerified -> Boolean.TRUE)
						.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.USER_PASSWORD_INVALID,
								AuthenticationPasswordType.OTP.getName(), AuthenticationPasswordType.OTP.getName())))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.verifyOtpResetPassword  : [" + authRequest.getInputPassType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	public Mono<Boolean> resetPassword(RequestUpdatePassword reqPassword) {

		OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

		AuthenticationRequest authRequest = reqPassword.getAuthRequest().setIdentifierType();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

					return this.findNonDeletedUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(), authRequest.getIdentifierType());
				},

				(ca, userTup) -> Mono.zip(
						this.checkUserAndClient(userTup, ca.getUrlClientCode()),
						this.checkUserStatus(userTup.getT3(), SecurityUserStatusCode.ACTIVE),
						(userClientCheck, userStatusCheck) -> userClientCheck && userStatusCheck)
						.flatMap(BooleanUtil::safeValueOfWithEmpty)
						.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.USER_NOT_ACTIVE)),

				(ca, userTup, userCheck) -> this.isPasswordUpdatable(ca, userTup.getT3(), reqPassword, Boolean.FALSE),

				(ca, userTup, userCheck, isUpdatable) -> this.otpService
						.verifyOtpInternal(ca.getUrlAppCode(), userTup.getT3(), purpose, authRequest.getOtp())
						.flatMap(BooleanUtil::safeValueOfWithEmpty)
						.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.USER_PASSWORD_INVALID,
								AuthenticationPasswordType.OTP.getName(), AuthenticationPasswordType.OTP.getName())),

				(ca, userTup, userCheck, isUpdatable, otpVerified) -> this.updatePasswordInternal(ca,
						userTup.getT3(), userTup.getT3().getId(), reqPassword.getPassType(),
						reqPassword.getNewPassword(), Boolean.TRUE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.resetPassword  : [" + authRequest.getInputPassType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	private Mono<Boolean> isPasswordUpdatable(ContextAuthentication ca, User user,
			RequestUpdatePassword reqPassword, boolean isUpdate) {

		if (StringUtil.safeIsBlank(reqPassword.getNewPassword()))
			return this.forbiddenError(SecurityMessageResourceService.NEW_PASSWORD_MISSING);

		boolean isSameUser = user.getClientId().equals(ULongUtil.valueOf(ca.getLoggedInFromClientId()));

		return FlatMapUtil.flatMapMono(
				() -> (isUpdate && isSameUser ? this.checkPasswordEquality(user, reqPassword) : Mono.just(Boolean.TRUE))
						.flatMap(BooleanUtil::safeValueOfWithEmpty)
						.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.OLD_NEW_PASSWORD_MATCH)),

				areEqual -> this.passwordPolicyCheck(ULongUtil.valueOf(ca.getLoggedInFromClientId()), null,
						ca.getUrlAppCode(), user.getId(), reqPassword.getPassType(), reqPassword.getNewPassword())
		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.isPasswordUpdatable"));
	}

	private Mono<Boolean> checkPasswordEquality(User user, RequestUpdatePassword reqPassword) {

		switch (reqPassword.getPassType()) {
			case PASSWORD -> {
				if (user.isPasswordHashed())
					return Mono.just(
							passwordEncoder.matches(user.getId() + reqPassword.getOldPassword(), user.getPassword())
									&& !passwordEncoder.matches(user.getId() + reqPassword.getNewPassword(),
											user.getPassword()));

				return Mono.just(!StringUtil.safeEquals(reqPassword.getNewPassword(), user.getPassword())
						&& StringUtil.safeEquals(reqPassword.getOldPassword(), user.getPassword()));
			}
			case PIN -> {
				if (user.isPinHashed())
					return Mono.just(passwordEncoder.matches(user.getId() + reqPassword.getOldPassword(), user.getPin())
							&& !passwordEncoder.matches(user.getId() + reqPassword.getNewPassword(), user.getPin()));

				return Mono.just(!StringUtil.safeEquals(reqPassword.getNewPassword(), user.getPin())
						&& StringUtil.safeEquals(reqPassword.getOldPassword(), user.getPin()));
			}
			default -> {
				return Mono.just(Boolean.FALSE);
			}
		}
	}

	private Mono<Boolean> updatePasswordInternal(ContextAuthentication ca, User user, ULong currentUserId,
			AuthenticationPasswordType passType, String newPassword, boolean isReset) {

		return FlatMapUtil.flatMapMono(

				() -> this.setPassword(user.getId(), currentUserId, newPassword, passType),

				passSet -> {
					this.soxLogService.createLog(user.getId(), SecuritySoxLogActionName.OTHER,
							SecuritySoxLogObjectName.USER, StringFormatter.format("$ updated", passType));

					return ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
							.setClientCode(ca.getUrlClientCode())
							.setEventName(isReset
									? EventNames.getEventName(EventNames.USER_PASSWORD_RESET_DONE, passType)
									: EventNames.getEventName(EventNames.USER_PASSWORD_CHANGED, passType))
							.setData(Map.of("user", user)));
				})
				.flatMap(e -> this.evictTokens(user.getId()).map(x -> e))
				.flatMap(e -> this.unlockUserInternal(user.getId()).map(x -> e))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateNewPassword"))
				.switchIfEmpty(this.forbiddenError("$ cannot be updated", passType));
	}

	private Mono<Boolean> setPassword(ULong userId, ULong currentUserId, String password,
			AuthenticationPasswordType passwordType) {

		if (currentUserId == null)
			currentUserId = userId;

		return this.dao.setPassword(userId, currentUserId, password, passwordType)
				.map(result -> result > 0)
				.flatMap(BooleanUtil::safeValueOfWithEmpty)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.setPassword"));
	}

	public Mono<List<UserClient>> findUserClients(AuthenticationRequest authRequest, ServerHttpRequest request) {

		String appCode = request.getHeaders()
				.getFirst(AppService.AC);

		String clientCode = request.getHeaders()
				.getFirst(ClientService.CC);

		return this.findUserClients(authRequest, appCode, clientCode, this.getNonDeletedUserStatusCodes());
	}

	private Mono<List<UserClient>> findUserClients(AuthenticationRequest authRequest, String appCode,
			String clientCode, SecurityUserStatusCode... userStatusCodes) {

		return this.dao.getAllClientsBy(authRequest.getUserName(), clientCode, appCode, authRequest.getIdentifierType(),
				userStatusCodes)
				.flatMapMany(map -> Flux.fromIterable(map.entrySet()))
				.flatMap(e -> this.clientService.getClientInfoById(e.getValue())
						.map(c -> Tuples.of(e.getKey(), c)))
				.collectList()
				.map(e -> e.stream()
						.map(x -> new UserClient(x.getT1(), x.getT2()))
						.sorted().toList());
	}

	// Don't call this method other than from the client service register method
	public Mono<User> createForRegistration(ULong appId, ULong appClientId, ULong urlClientId, Client client,
			User user, AuthenticationPasswordType passwordType) {

		String password = user.getInputPass(passwordType);
		user.setPassword(null);
		user.setPasswordHashed(false);
		user.setAccountNonExpired(true);
		user.setAccountNonLocked(true);
		user.setCredentialsNonExpired(true);

		return FlatMapUtil.flatMapMono(

				() -> this.dao.checkUserExists(user.getClientId(), user.getUserName(), user.getEmailId(),
						user.getPhoneNumber(), "INDV")
						.filter(userExists -> !userExists).map(userExists -> Boolean.FALSE),

				userExists -> this.dao.create(user),

				(userExists, createdUser) -> {
					this.soxLogService.createLog(createdUser.getId(), CREATE, getSoxObjectName(), "User created");

					return this.setPassword(createdUser.getId(), createdUser.getId(), password, passwordType);
				},

				(userExists, createdUser, passSet) -> {
					Mono<Boolean> roleUser = FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.addDefaultRoles(appId, appClientId, urlClientId, client,
									createdUser.getId()))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"));

					return roleUser.map(x -> createdUser);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_CREATE, "User"));
	}

	private Mono<Boolean> addDefaultRoles(ULong appId, ULong appClientId, ULong urlClientId, Client client,
			ULong userId) {

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientLevelType(client.getId(), appId),

				levelType -> this.appRegistrationDAO.getRoleIdsForRegistration(appId, appClientId, urlClientId,
						client.getTypeCode(), levelType, client.getBusinessType()),

				(levelType, roles) -> Flux.fromIterable(roles)
						.flatMap(roleId -> this.dao.addRoleToUser(userId, roleId)).collectList(),

				(levelType, roles, addedRoles) -> Mono.just(true)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.addDefaultRoles"));
	}

	@PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
	public Mono<Boolean> makeUserActive(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.dao.makeUserActiveIfInActive(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserActive"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
	}

	@PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
	public Mono<Boolean> makeUserInActive(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.dao.makeUserInActive(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserInActive"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
	}

	@PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
	public Mono<Boolean> unblockUser(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.unlockUserInternal(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.unblockUser"))
				.switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
	}

	public Mono<Boolean> lockUserInternal(ULong userId, LocalDateTime lockUntil, String lockedDueTo) {
		return this.dao.lockUser(userId, lockUntil, lockedDueTo);
	}

	public Mono<Boolean> unlockUserInternal(ULong userId) {
		return this.dao.updateUserStatusToActive(userId);
	}

	public Mono<Boolean> checkIndividualClientUser(String urlClientCode, ClientRegistrationRequest request) {
		return this.clientService.getClientId(urlClientCode).flatMap(clientId -> this.dao.checkUserExists(clientId,
				request.getUserName(), request.getEmailId(), request.getPhoneNumber(), "INDV"));
	}

	public Mono<TokenObject> makeOneTimeToken(ServerHttpRequest httpRequest, ContextAuthentication ca, User user,
			ULong loggedInClientId) {

		String host = httpRequest.getURI().getHost();

		String port = "" + httpRequest.getURI().getPort();

		List<String> forwardedHost = httpRequest.getHeaders().get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.getFirst();
		}

		List<String> forwardedPort = httpRequest.getHeaders().get("X-Forwarded-Port");

		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.getFirst();
		}

		InetSocketAddress inetAddress = httpRequest.getRemoteAddress();
		final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

		Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(JWTGenerateTokenParameters.builder()
				.userId(user.getId().toBigInteger())
				.secretKey(tokenKey)
				.expiryInMin(VALIDITY_MINUTES)
				.host(host)
				.port(port)
				.loggedInClientId(loggedInClientId.toBigInteger())
				.loggedInClientCode(ca.getUrlClientCode())
				.oneTime(true)
				.build());

		return tokenService.create(new TokenObject().setUserId(user.getId())
				.setToken(token.getT1())
				.setPartToken(token.getT1()
						.length() < 50 ? token.getT1()
								: token.getT1()
										.substring(token.getT1()
												.length() - 50))
				.setExpiresAt(token.getT2())
				.setIpAddress(hostAddress));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User') and hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> copyUserRolesNPermissions(ULong userId, ULong referenceUserId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.readById(userId),

				(ca, user) -> this.dao.readById(referenceUserId),

				(ca, user, rUser) -> Mono.just(user.getClientId().equals(rUser.getClientId()))
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, user, rUser, isSameClient) -> ca.isSystemClient() ? Mono.just(true) :

						clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()),

								user.getClientId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, user, rUser, isSameClient, sysOrManaged) -> this.dao.copyRolesNPermissionsFromUser(userId,
						referenceUserId)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.copyUserRolesNPermissions"))
				.switchIfEmpty(
						this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_COPY_ROLE_PERMISSION, "User"));
	}
}
