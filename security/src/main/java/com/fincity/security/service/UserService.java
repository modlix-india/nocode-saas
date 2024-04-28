package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;
import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserClient;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.ClientRegistrationRequest;
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
	private final ClientPasswordPolicyService clientPasswordPolicyService;
	private final PasswordEncoder passwordEncoder;
	private final SecurityMessageResourceService securityMessageResourceService;
	private final SoxLogService soxLogService;
	private final TokenService tokenService;
	private final EventCreationService ecService;
	private final AppRegistrationDAO appRegistrationDAO;

	@Value("${jwt.key}")
	private String tokenKey;

	public UserService(ClientService clientService, ClientPasswordPolicyService clientPasswordPolicyService,
			PasswordEncoder passwordEncoder, SecurityMessageResourceService securityMessageResourceService,
			SoxLogService soxLogService, TokenService tokenService, EventCreationService ecService,
			AppRegistrationDAO appRegistrationDAO) {

		this.clientService = clientService;
		this.clientPasswordPolicyService = clientPasswordPolicyService;
		this.passwordEncoder = passwordEncoder;
		this.securityMessageResourceService = securityMessageResourceService;
		this.soxLogService = soxLogService;
		this.tokenService = tokenService;
		this.ecService = ecService;
		this.appRegistrationDAO = appRegistrationDAO;
	}

	public Mono<User> findByUserName(ULong clientId, String userName,
			AuthenticationIdentifierType authenticationIdentifierType) {

		return this.dao.getBy(clientId, userName, authenticationIdentifierType)
				.flatMap(this.dao::setPermissions);
	}

	public Mono<Tuple3<Client, Client, User>> findUserNClient(String userName, ULong userId, String clientCode,
			String appCode, AuthenticationIdentifierType authenticationIdentifierType, boolean onlyActiveUsers) {

		return FlatMapUtil.flatMapMono(

				() -> this.dao
						.getBy(userName, userId, clientCode, appCode, authenticationIdentifierType, onlyActiveUsers)
						.flatMap(users -> Mono.justOrEmpty(users.size() != 1 ? null : users.get(0)))
						.flatMap(this.dao::setPermissions),

				user -> this.clientService.getClientInfoById(user.getClientId()),

				(user, client) -> {
					if (client.getCode().equals(clientCode))
						return Mono.just(client);

					return this.clientService.getClientBy(clientCode);
				},

				(user, client, mClient) -> Mono.just(Tuples.<Client, Client, User>of(mClient, client, user)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.findUserNClient"));
	}

	public Mono<User> getUserForContext(ULong id) {
		return this.dao.readById(id)
				.flatMap(this.dao::setPermissions);
	}

	public Mono<Object> increaseFailedAttempt(ULong userId) {

		return this.dao.increaseFailedAttempt(userId);
	}

	public Mono<Object> resetFailedAttempt(ULong userId) {
		return this.dao.resetFailedAttempt(userId);
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

		String password = entity.getPassword();
		entity.setPassword(null);
		entity.setPasswordHashed(false);

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					if (entity.getClientId() == null) {
						entity.setClientId(ULong.valueOf(ca.getUser()
								.getClientId()));
						return Mono.just(entity);
					}

					if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
						return Mono.just(entity);

					return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							.getClientId()), entity.getClientId())
							.flatMap(e -> e.booleanValue() ? Mono.just(entity) : Mono.empty());
				})
				.flatMap(e -> this.passwordPolicyCheck(e, password))
				.flatMap(u -> this.dao
						.checkAvailabilityWithClientId(u.getClientId(), u.getUserName(), u.getEmailId(),
								u.getPhoneNumber())
						.map(b -> u))
				.flatMap(u -> this.dao.create(u))
				.flatMap(u -> {

					this.soxLogService.create(

							new SoxLog().setActionName(CREATE)
									.setObjectId(u.getId())
									.setObjectName(getSoxObjectName())
									.setDescription("User created"))
							.subscribe();

					return this.setPassword(u, password);
				})
				.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
						.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
						.flatMap(msg -> Mono.error(
								new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User"))))));
	}

	private Mono<User> passwordPolicyCheck(User user, String password) {

		return this.clientService.validatePasswordPolicy(user.getClientId(), password)
				.map(e -> user);
	}

	private Mono<User> setPassword(User u, String password) {

		return flatMapMono(

				this::getLoggedInUserId,

				loggedInUserId -> this.dao.setPassword(u.getId(), password, loggedInUserId)
						.map(result -> result > 0)
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(loggedInUserId, passwordSet) -> Mono.just(u)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.setPassword"));

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
						.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
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

		String userName = null;
		String emailId = null;
		String phoneNumber = null;

		if (fields.containsKey("userName"))
			userName = fields.get("userName")
					.toString();

		if (fields.containsKey("emailId"))
			userName = fields.get("emailId")
					.toString();

		if (fields.containsKey("phoneNumber"))
			userName = fields.get("phoneNumber")
					.toString();

		return this.dao.checkAvailability(key, userName, emailId, phoneNumber)
				.flatMap(e -> super.update(key, fields))
				.flatMap(e -> this.evictTokens(e.getId())
						.map(x -> e));
	}

	@Override
	public Mono<User> update(User entity) {

		return this.dao
				.checkAvailability(entity.getId(), entity.getUserName(), entity.getEmailId(), entity.getPhoneNumber())
				.map(BooleanUtil::safeValueOf)
				.flatMap(e -> super.update(entity))
				.flatMap(e -> this.evictTokens(e.getId())
						.map(x -> e));
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

		return flatMapMono(

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

		return flatMapMono(

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

	public Mono<Boolean> removePermission(ULong userId, ULong permissionId) {
		return this.dao.removePermissionFromUser(userId, permissionId)
				.map(value -> value > 0)
				.flatMap(e -> this.evictTokens(userId)
						.map(x -> e));
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

		return flatMapMono(

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
		return flatMapMono(

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

					if (result.booleanValue())
						return Mono.just(result);

					return flatMapMono(

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
										if (e.booleanValue())
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
					if (result.booleanValue())
						return Mono.just(result);

					return flatMapMono(

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
										if (e.booleanValue())
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

	public Mono<Boolean> isBeingManagedBy(ULong loggedInClientId, ULong userId) {
		return this.dao.isBeingManagedBy(loggedInClientId, userId);
	}

	public Mono<Boolean> checkRoleCreatedByUser(ULong userId, ULong roleId) {
		return this.dao.checkRoleCreatedByUser(roleId, userId);
	}

	public Mono<Boolean> updateNewPassword(String urlAppCode, String urlClientCode, ULong reqUserId,
			RequestUpdatePassword requestPassword, boolean isResetPassword) {

		if (StringUtil.safeIsBlank(requestPassword.getNewPassword()))
			return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
					SecurityMessageResourceService.NEW_PASSWORD_MISSING);

		return flatMapMono(

				() -> this.dao.readById(reqUserId),

				user -> this.checkHierarchy(user, reqUserId, requestPassword.getNewPassword()),

				(user, isUpdatable) -> this.clientPasswordPolicyService.checkAllConditions(user.getClientId(),
						requestPassword.getNewPassword()),

				(user, isUpdatable, isValid) -> isResetPassword ? Mono.just(true)
						: this.checkPasswordInPastPasswords(user, requestPassword.getNewPassword()),

				(user, isUpdatable, isValid, isPastPassword) -> this.dao
						.setPassword(reqUserId, requestPassword.getNewPassword(), user.getId())
						.flatMap(e -> {
							this.soxLogService.create(new SoxLog().setObjectId(reqUserId)
									.setActionName(SecuritySoxLogActionName.OTHER)
									.setObjectName(SecuritySoxLogObjectName.USER)
									.setDescription("Password updated"))
									.subscribe();

							return ecService.createEvent(new EventQueObject().setAppCode(urlAppCode)
									.setClientCode(urlClientCode)
									.setEventName(isResetPassword ? EventNames.USER_PASSWORD_RESET_DONE
											: EventNames.USER_PASSWORD_CHANGED)
									.setData(Map.of("user", user)))
									.map(x -> e > 0);
						}))

				.flatMap(e -> this.evictTokens(reqUserId)
						.map(x -> e))
				.flatMap(e -> this.dao.updateUserStatus(reqUserId)
						.map(x -> e))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateNewPassword"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "Password cannot be updated"));

	}

	// check the hierarchy with client id and user id as per the comment given
	// 1.) same user id
	// 2.) logged in user's client id matches given user's client id if logged in
	// user has user edit access update the password
	// 3.) logged in user's client id is system if logged in user has user edit
	// access update the password
	// 4.) if no client id matches check logged in user's client id is managing user
	// id's client id if logged in user has user edit access the update password

	public Mono<Boolean> checkHierarchy(User user, ULong reqUserId, String newPassword) {

		if (user.getId()
				.equals(reqUserId)) {

			return flatMapMono(

					() -> this.dao.readById(reqUserId),

					requestedUser -> Mono.just(SecurityUserStatusCode.ACTIVE.equals(requestedUser.getStatusCode())),

					(requestedUser, isActive) -> isActive.booleanValue()
							? this.checkPasswordEquality(requestedUser, newPassword)
							: securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									SecurityMessageResourceService.USER_NOT_ACTIVE),

					(requestedUser, isActive, passwordEqual) -> passwordEqual.booleanValue()
							? securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									SecurityMessageResourceService.OLD_NEW_PASSWORD_MATCH)
							: Mono.just(true)

			).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy"));
		}

		return flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(ca.isSystemClient()),

				(ca, isSys) -> isSys.booleanValue() ? Mono.justOrEmpty(Optional.empty())
						: this.dao.readById(reqUserId)
								.map(User::getClientId),
				(ca, isSys, rClientId) -> {

					if (isSys.booleanValue())
						return Mono.just(true);

					return Mono.just(rClientId.toBigInteger()
							.equals(ca.getUser()
									.getClientId()));
				},

				(ca, isSys, rClientId, isSameClient) -> {

					if (isSameClient.booleanValue())
						return Mono.just(true);

					return this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							.getClientId()), rClientId);
				},

				(ca, isSys, rclientId, isSameClient, isManaged) -> {

					if (!isManaged.booleanValue())
						return Mono.just(false);

					return SecurityContextUtil.hasAuthority("Authorities.User_UPDATE");
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy"));
	}

	public Mono<Boolean> checkPasswordEquality(User u, String newPassword) {

		if (u.isPasswordHashed()) {
			if (passwordEncoder.matches(u.getId() + newPassword, u.getPassword()))
				return Mono.just(true);
		} else if (StringUtil.safeEquals(newPassword, u.getPassword()))
			return Mono.just(true);

		return Mono.just(false);
	}

	private Mono<Boolean> checkPasswordInPastPasswords(User user, String newPassword) {

		return flatMapMono(

				() -> this.dao.getPastPasswordsBasedOnPolicy(user.getId(), user.getClientId()),

				pastPasswords -> {

					for (var pastPassword : pastPasswords) {
						if ((pastPassword.isPasswordHashed()
								&& passwordEncoder.matches(pastPassword.getPassword(), user.getId() + newPassword))
								|| (!pastPassword.isPasswordHashed() && pastPassword.getPassword()
										.equals(newPassword)))
							return this.securityMessageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
									SecurityMessageResourceService.PASSWORD_USER_ERROR);

					}

					return Mono.just(true);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkPasswordInPastPasswords"));

	}

	public Mono<List<UserClient>> findUserClients(AuthenticationRequest authRequest, ServerHttpRequest request) {

		String appCode = request.getHeaders()
				.getFirst("appCode");

		String clientCode = request.getHeaders()
				.getFirst("clientCode");

		return this.findUserClients(authRequest, appCode, clientCode);
	}

	public Mono<List<UserClient>> findUserClients(AuthenticationRequest authRequest, String appCode,
			String clientCode) {

		return this.dao.getAllClientsBy(authRequest.getUserName(), clientCode, appCode, authRequest.getIdentifierType())
				.flatMapMany(map -> Flux.fromIterable(map.entrySet()))
				.flatMap(e -> this.clientService.getClientInfoById(e.getValue())
						.map(c -> Tuples.of(e.getKey(), c)))
				.collectList()
				.map(e -> e.stream()
						.map(x -> new UserClient(x.getT1(), x.getT2()))
						.sorted()
						.toList());
	}

	// Don't call this method other than from the client service register method
	public Mono<User> createForRegistration(ULong appId, ULong appClientId, ULong urlClientId, Client client,
			User user) {

		String password = user.getPassword();
		user.setPassword(null);
		user.setPasswordHashed(false);
		user.setAccountNonExpired(true);
		user.setAccountNonLocked(true);
		user.setCredentialsNonExpired(true);

		return FlatMapUtil.flatMapMono(

				() -> this.passwordPolicyCheck(user, password),

				u -> this.dao
						.checkAvailabilityWithClientId(u.getClientId(), u.getUserName(), u.getEmailId(),
								u.getPhoneNumber())
						.map(b -> u),

				(u, cu) -> this.dao.create(cu),

				(u, cu, createdUser) -> {
					this.soxLogService.create(

							new SoxLog().setActionName(CREATE)
									.setObjectId(createdUser.getId())
									.setObjectName(getSoxObjectName())
									.setDescription("User created"))
							.subscribe();

					return this.setPassword(createdUser, password);
				},

				(u, cu, createdUser, spu) -> {
					Mono<Boolean> roledUser = FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.addDefaultRoles(appId, appClientId, urlClientId, client,
									createdUser.getId()))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"));

					return roledUser.map(x -> createdUser);
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"))
				.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
						.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
						.flatMap(msg -> Mono.error(
								new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User"))))));
	}

	public Mono<Boolean> addDefaultRoles(ULong appId, ULong appClientId, ULong urlClientId, Client client,
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

	public Mono<Boolean> makeUserActive() {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.makeUserActiveIfInActive(ca.getUser()
						.getId()))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserActive"));
	}

	public Mono<Boolean> checkUserExists(String urlAppCode, String urlClientCode, ClientRegistrationRequest request) {

		return this.dao.checkUserExists(urlAppCode, urlClientCode, request);
	}

	public Mono<Boolean> resetPasswordRequest(AuthenticationRequest authRequest, ServerHttpRequest request) {

		LogUtil.logIfDebugKey(logger, "Requesting reset password.");

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated()) {
						return this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.PASS_RESET_REQ_ERROR);
					}

					return this.findUserNClient(authRequest.getUserName(), authRequest.getUserId(),
							ca.getUrlClientCode(), ca.getUrlAppCode(), authRequest.getIdentifierType(), false);
				},

				(ca, cuTup) -> this.clientService.getClientBy(ca.getUrlClientCode())
						.map(Client::getId),

				(ca, cu, loggedInClientId) -> this.makeOneTimeToken(request, ca, cu.getT3(), loggedInClientId),

				(ca, cu, loggedInClientId,
						token) -> ecService.createEvent(new EventQueObject().setAppCode(ca.getUrlAppCode())
								.setClientCode(ca.getUrlClientCode())
								.setEventName(EventNames.USER_RESET_PASSWORD_REQUEST)
								.setData(Map.of("user", cu.getT3(), "token", token.getToken())))

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.resetPasswordRequest"))
				.map(e -> true);
	}

	public Mono<TokenObject> makeOneTimeToken(ServerHttpRequest httpRequest, ContextAuthentication ca, User u,
			ULong loggedInClientCode) {
		String host = httpRequest.getURI()
				.getHost();
		String port = "" + httpRequest.getURI()
				.getPort();

		List<String> forwardedHost = httpRequest.getHeaders()
				.get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		List<String> forwardedPort = httpRequest.getHeaders()
				.get("X-Forwarded-Port");

		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.get(0);
		}

		InetSocketAddress inetAddress = httpRequest.getRemoteAddress();
		final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

		Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(JWTGenerateTokenParameters.builder()
				.userId(u.getId()
						.toBigInteger())
				.secretKey(tokenKey)
				.expiryInMin(VALIDITY_MINUTES)
				.host(host)
				.port(port)
				.loggedInClientId(loggedInClientCode.toBigInteger())
				.loggedInClientCode(ca.getUrlClientCode())
				.oneTime(true)
				.build());

		return tokenService.create(new TokenObject().setUserId(u.getId())
				.setToken(token.getT1())
				.setPartToken(token.getT1()
						.length() < 50 ? token.getT1()
								: token.getT1()
										.substring(token.getT1()
												.length() - 50))
				.setExpiresAt(token.getT2())
				.setIpAddress(hostAddress));
	}
}
