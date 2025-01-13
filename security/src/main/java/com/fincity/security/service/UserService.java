package com.fincity.security.service;

import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
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

	public Mono<Tuple3<Client, Client, User>> findNonDeletedUserNClient(String userName, ULong userId,
			String clientCode, String appCode, AuthenticationIdentifierType authenticationIdentifierType) {
		return findUserNClient(userName, userId, clientCode, appCode, authenticationIdentifierType,
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

		return Mono.justOrEmpty(
				ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(userNClient.getT1().getTypeCode()) ||
						clientCode.equals(userNClient.getT1().getCode()) ||
						userNClient.getT1().getId().equals(userNClient.getT2().getId()) ? Boolean.TRUE : Boolean.FALSE);
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

				}

		).switchIfEmpty(Mono.defer(() -> securityMessageResourceService
				.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE, "User")
				.flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN, msg)))));
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
		if (!entity.checkIdentificationKeys())
			return securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
					SecurityMessageResourceService.USER_IDENTIFICATION_NOT_FOUND);

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
						return Mono.defer(() -> securityMessageResourceService
								.getMessage(SecurityMessageResourceService.FORBIDDEN_PERMISSION)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
										StringFormatter.format(msg, "User READ")))));

					return Mono.just(e);
				}))
				.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
						.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
						.flatMap(msg -> Mono.error(
								new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User", id))))));
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
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
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
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
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
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FETCH_PERMISSION_ERROR_FOR_USER, userId));

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
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FETCH_ROLE_ERROR_FOR_USER, userId));

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
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.REMOVE_PERMISSION_ERROR, permissionId, userId));
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
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
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
							.flatMap(e -> this.evictTokens(userId)
									.map(x -> e))
							.switchIfEmpty(securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR, permissionId, userId));
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
							.flatMap(e -> this.evictTokens(userId)
									.map(x -> e))
							.switchIfEmpty(securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									SecurityMessageResourceService.ROLE_FORBIDDEN, roleId, userId));
				});

	}

	public Mono<Boolean> updateNewPassword(ULong reqUserId, RequestUpdatePassword reqPassword,
			AuthenticationPasswordType passwordType) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.readById(reqUserId),

				(ca, user) -> this.isPasswordUpdatable(ca, user, reqPassword, Boolean.FALSE, passwordType)
						.filter(isUpdatable -> isUpdatable).map(isUpdatable -> Boolean.TRUE),

				(ca, user, isUpdatable) -> this.updateNewPassword(ca, user, reqPassword, Boolean.FALSE, passwordType))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateNewPassword : [" + passwordType + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	public Mono<Boolean> generateOtpResetPassword(AuthenticationRequest authRequest, ServerHttpRequest request) {

		OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

		if (authRequest.getIdentifierType() == null)
			authRequest.setIdentifierType();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

					return this.findNonDeletedUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(), authRequest.getIdentifierType());
				},

				(ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
						.filter(userCheck -> userCheck).map(userCheck -> Boolean.TRUE),

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
						"UserService.generateOtpResetPassword : [" + authRequest.getPasswordType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	public Mono<Boolean> resetPassword(RequestUpdatePassword reqPassword) {

		OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

		AuthenticationRequest authRequest = reqPassword.getAuthRequest().setIdentifierType();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

					return this.findNonDeletedUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(), authRequest.getIdentifierType());
				},

				(ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
						.filter(userCheck -> userCheck).map(userCheck -> Boolean.TRUE),

				(ca, userTup, userCheck) -> this
						.isPasswordUpdatable(ca, userTup.getT3(), reqPassword, Boolean.TRUE, reqPassword.getPassType())
						.filter(isUpdatable -> isUpdatable).map(isUpdatable -> Boolean.TRUE),

				(ca, userTup, userCheck, isUpdatable) -> this.otpService
						.verifyOtpInternal(ca.getUrlAppCode(), userTup.getT3(), purpose, authRequest.getOtp())
						.filter(otpVerified -> userCheck).map(otpVerified -> Boolean.TRUE)
						.switchIfEmpty(this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								SecurityMessageResourceService.USER_PASSWORD_INVALID,
								AuthenticationPasswordType.OTP.getName(), AuthenticationPasswordType.OTP.getName())),

				(ca, userTup, userCheck, isUpdatable, otpVerified) -> this.updateNewPassword(ca, userTup.getT3(),
						reqPassword, Boolean.TRUE, reqPassword.getPassType()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserService.resetPassword  : [" + authRequest.getPasswordType() + "]"))
				.switchIfEmpty(Mono.just(Boolean.FALSE)).log();
	}

	private Mono<Boolean> isPasswordUpdatable(ContextAuthentication ca, User user, RequestUpdatePassword reqPassword,
			boolean isReset, AuthenticationPasswordType passwordType) {

		if (StringUtil.safeIsBlank(reqPassword.getNewPassword()))
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
					SecurityMessageResourceService.NEW_PASSWORD_MISSING);

		return FlatMapUtil.flatMapMono(

				() -> this.checkHierarchy(user, reqPassword, isReset, passwordType),

				isUpdatable -> this.passwordPolicyCheck(ULongUtil.valueOf(ca.getLoggedInFromClientId()), null,
						ca.getUrlAppCode(), user.getId(), passwordType, reqPassword.getNewPassword()));
	}

	/**
	 * Checks the hierarchy between the logged-in user and the given user to
	 * determine if the password update
	 * is allowed based on the specified conditions.
	 *
	 * <p>
	 * This method evaluates the following hierarchy conditions:
	 * <ol>
	 * <li>The logged-in user and the target user have the same user ID.</li>
	 * <li>The logged-in user's client ID matches the target user's client ID, and
	 * the logged-in user has
	 * "User_EDIT" authority to update the password.</li>
	 * <li>The logged-in user's client ID is "system", and the logged-in user has
	 * "User_EDIT" authority
	 * to update the password.</li>
	 * <li>If no client ID matches, the logged-in user's client ID must manage the
	 * target user's client ID,
	 * and the logged-in user must have "User_EDIT" authority to update the
	 * password.</li>
	 * </ol>
	 *
	 * <p>
	 * If any of the above conditions are satisfied, the password update is allowed.
	 * Otherwise, an exception is thrown.
	 *
	 * @param user            The {@link User} whose password hierarchy needs to be
	 *                        checked.
	 * @param reqPassword     The {@link RequestUpdatePassword} containing the
	 *                        password update request details.
	 * @param isResetPassword A flag indicating whether this is a password reset
	 *                        operation.
	 * @param passwordType    The {@link AuthenticationPasswordType} indicating the
	 *                        type of password authentication.
	 * @return A {@link Mono<Boolean>} indicating whether the password update
	 *         operation is allowed.
	 * @throws GenericException If the user hierarchy conditions are not satisfied
	 *                          or if any other validation fails.
	 */
	private Mono<Boolean> checkHierarchy(User user, RequestUpdatePassword reqPassword, boolean isResetPassword,
			AuthenticationPasswordType passwordType) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ULongUtil.valueOf(ca.getUser().getId()).equals(user.getId()) && !isResetPassword) {

						return FlatMapUtil.flatMapMono(

								() -> SecurityUserStatusCode.ACTIVE.equals(user.getStatusCode())
										? this.checkPasswordEquality(user, reqPassword, passwordType)
										: securityMessageResourceService.throwMessage(
												msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
												SecurityMessageResourceService.USER_NOT_ACTIVE),

								passwordEqual -> Boolean.TRUE.equals(passwordEqual) ? Mono.just(Boolean.TRUE)
										: securityMessageResourceService.throwMessage(
												msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
												SecurityMessageResourceService.OLD_NEW_PASSWORD_MATCH))
								.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy"))
								.log();
					}

					ULong loggedInUserClientId = ULong.valueOf(ca.getUser().getClientId());

					return FlatMapUtil.flatMapMono(

							() -> Mono.just(ca.isSystemClient() || user.getClientId()
									.equals(loggedInUserClientId)),

							sysOrSame -> Boolean.TRUE.equals(sysOrSame) ? Mono.just(Boolean.TRUE)
									: this.clientService.isBeingManagedBy(loggedInUserClientId, user.getClientId())
											.flatMap(BooleanUtil::safeValueOfWithEmpty)
											.flatMap(e -> SecurityContextUtil.hasAuthority("Authorities.User_UPDATE"))
											.flatMap(BooleanUtil::safeValueOfWithEmpty))
							.switchIfEmpty(securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									SecurityMessageResourceService.HIERARCHY_ERROR));

				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy")).log();
	}

	private Mono<Boolean> updateNewPassword(ContextAuthentication ca, User user,
			RequestUpdatePassword reqPassword, boolean isReset, AuthenticationPasswordType passwordType) {

		ULong currentUserId = ca.isAuthenticated() ? ULongUtil.valueOf(ca.getUser().getId()) : user.getId();

		return FlatMapUtil.flatMapMono(

				() -> this.setPassword(user.getId(), currentUserId, reqPassword.getNewPassword(), passwordType),
				passSet -> {
					this.soxLogService.createLog(user.getId(), SecuritySoxLogActionName.OTHER,
							SecuritySoxLogObjectName.USER,
							StringFormatter.format("$ updated", passwordType.getName()));

					return ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
							.setClientCode(ca.getUrlClientCode())
							.setEventName(isReset
									? EventNames.getEventName(EventNames.USER_PASSWORD_RESET_DONE, passwordType)
									: EventNames.getEventName(EventNames.USER_PASSWORD_CHANGED, passwordType))
							.setData(Map.of("user", user)));
				})
				.flatMap(e -> this.evictTokens(user.getId()).map(x -> e))
				.flatMap(e -> this.unlockUserInternal(user.getId()).map(x -> e))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateNewPassword"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "$ cannot be updated", passwordType));
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

	private Mono<Boolean> checkPasswordEquality(User user, RequestUpdatePassword reqPassword,
			AuthenticationPasswordType passwordType) {

		switch (passwordType) {
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

		String password = user.getPassword();
		user.setPassword(null);
		user.setPasswordHashed(false);
		user.setAccountNonExpired(true);
		user.setAccountNonLocked(true);
		user.setCredentialsNonExpired(true);

		return FlatMapUtil.flatMapMono(

				() -> this.passwordPolicyCheck(urlClientId, appId, null, null, passwordType, password),

				passwordCheck -> this.dao
						.checkUserExists(user.getClientId(), user.getUserName(), user.getEmailId(),
								user.getPhoneNumber(), "INDV")
						.filter(userExists -> !userExists).map(userExists -> Boolean.FALSE),

				(passwordCheck, userExists) -> this.dao.create(user),

				(passwordCheck, userExists, createdUser) -> {
					this.soxLogService.createLog(createdUser.getId(), CREATE, getSoxObjectName(), "User created");

					return this.setPassword(createdUser.getId(), createdUser.getId(), password,
							AuthenticationPasswordType.PASSWORD);
				},

				(passwordCheck, userExists, createdUser, passSet) -> {
					Mono<Boolean> roleUser = FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.addDefaultRoles(appId, appClientId, urlClientId, client,
									createdUser.getId()))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"));

					return roleUser.map(x -> createdUser);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"))
				.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
						.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
						.flatMap(msg -> Mono.error(
								new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User"))))));
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

				ca -> Mono.just(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.dao.makeUserActiveIfInActive(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserActive"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));

	}

	@PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
	public Mono<Boolean> makeUserInActive(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.dao.makeUserInActive(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserInActive"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));

	}

	@PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
	public Mono<Boolean> unblockUser(ULong userId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(CommonsUtil.nonNullValue(userId, ULong.valueOf(ca.getUser()
						.getId()))),

				(ca, id) -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
						: this.dao.readById(id)
								.flatMap(e -> this.clientService.isBeingManagedBy(
										ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),

				(ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged) ? Mono.empty()
						: this.unlockUserInternal(id))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.unblockUser"))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));

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
				.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
						.getMessage(SecurityMessageResourceService.FORBIDDEN_COPY_ROLE_PERMISSION)
						.flatMap(msg -> Mono.error(
								new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User"))))));
	}
}
