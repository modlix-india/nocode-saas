package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;
import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.RequestUpdatePassword;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class UserService extends AbstractSecurityUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

	private static final String ASSIGNED_PERMISSION = " Permission is assigned to the user ";

	private static final String ASSIGNED_ROLE = " Role is assigned to the user ";

	private static final String UNASSIGNED_PERMISSION = " Permission is removed from the selected user";

	private static final String UNASSIGNED_ROLE = " Role is removed from the selected user";

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientPasswordPolicyService clientPasswordPolicyService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private SoxLogService soxLogService;

	@Autowired
	private TokenService tokenService;

	public Mono<User> findByUserName(ULong clientId, String userName,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		return this.dao.getBy(clientId, userName, authenticationIdentifierType)
		        .flatMap(this.dao::setPermissions);
	}

	public Mono<Tuple3<Client, Client, User>> findUserNClient(String userName, ULong userId, String appCode,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		return FlatMapUtil.flatMapMonoLog(

		        () -> this.dao.getBy(userName, userId, appCode, authenticationIdentifierType)
		                .flatMap(users -> Mono.justOrEmpty(users.size() != 1 ? null : users.get(0))),

		        user -> this.clientService.getClientInfoById(user.getClientId()
		                .toBigInteger()),

		        (user, client) -> this.clientService.getManagedClientOfClientById(user.getClientId())
		                .defaultIfEmpty(client),

		        (user, client, mClient) -> Mono.just(Tuples.<Client, Client, User>of(mClient, client, user)));
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

	@PreAuthorize("hasPermission('Authorities.User_CREATE')")
	@Override
	public Mono<User> create(User entity) {

		String password = entity.getPassword();
		entity.setPassword(null);
		entity.setPasswordHashed(false);

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

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
		        .flatMap(u ->
				{

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

		);

	}

	@Override
	public Mono<User> read(ULong id) {

		return super.read(id).flatMap(e -> SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
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
		        .map(e ->
				{
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
		        .map(e ->
				{
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
				        .map(val ->
						{
					        boolean removed = val > 0;

					        if (removed)
						        super.unAssignLog(userId, UNASSIGNED_PERMISSION);

					        return removed;
				        })

		)

		        .flatMap(e -> this.evictTokens(userId)
		                .map(x -> e))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
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
				        .map(val ->
						{
					        boolean removed = val > 0;
					        if (removed)
						        super.unAssignLog(userId, UNASSIGNED_ROLE);

					        return removed;
				        })

		)

		        .flatMap(e -> this.evictTokens(userId)
		                .map(x -> e))
		        .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> assignPermissionToUser(ULong userId, ULong permissionId) {

		return this.dao.checkPermissionAssignedForUser(userId, permissionId)
		        .flatMap(result ->
				{

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
							        .map(e ->
									{
								        if (e.booleanValue())
									        super.assignLog(userId, ASSIGNED_PERMISSION + permissionId);

								        return e;
							        })

				).flatMap(e -> this.evictTokens(userId)
				        .map(x -> e))
			                .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR, permissionId, userId));
		        });

	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User')")
	public Mono<Boolean> assignRoleToUser(ULong userId, ULong roleId) {

		return this.dao.checkRoleAssignedForUser(userId, roleId)
		        .flatMap(result ->
				{
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
							        .map(e ->
									{
								        if (e.booleanValue())
									        super.assignLog(userId, ASSIGNED_ROLE + roleId);

								        return e;
							        })

				)

			                .flatMap(e -> this.evictTokens(userId)
			                        .map(x -> e))
			                .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.ROLE_FORBIDDEN, roleId, userId));

		        });

	}

	public Mono<Boolean> isBeingManagedBy(ULong loggedInClientId, ULong userId) {
		return this.dao.isBeingManagedBy(loggedInClientId, userId);
	}

	public Mono<Boolean> checkRoleCreatedByUser(ULong userId, ULong roleId) {
		return this.dao.checkRoleCreatedByUser(roleId, userId);
	}

	public Mono<Boolean> updateNewPassword(ULong reqUserId, RequestUpdatePassword requestPassword) {

		if (StringUtil.safeIsBlank(requestPassword.getNewPassword()))
			return securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			        SecurityMessageResourceService.NEW_PASSWORD_MISSING);

		return flatMapMono(

		        () -> this.dao.readById(reqUserId),

		        user -> this.checkHierarchy(user, reqUserId, requestPassword.getNewPassword()),

		        (user, isUpdatable) -> this.clientPasswordPolicyService.checkAllConditions(user.getClientId(),
		                requestPassword.getNewPassword()),

		        (user, isUpdatable, isValid) -> this.checkPasswordInPastPasswords(user,
		                requestPassword.getNewPassword()),

		        (user, isUpdatable, isValid, isPastPassword) -> this.dao
		                .setPassword(reqUserId, requestPassword.getNewPassword(), user.getId())
		                .map(e ->
						{
			                this.soxLogService.create(new SoxLog().setObjectId(reqUserId)
			                        .setActionName(SecuritySoxLogActionName.OTHER)
			                        .setObjectName(SecuritySoxLogObjectName.USER)
			                        .setDescription("Password updated"))
			                        .subscribe();

			                return e > 0;
		                }))

		        .flatMap(e -> this.evictTokens(reqUserId)
		                .map(x -> e))

		        .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                "Password cannot be updated"));

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
			                : securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.USER_NOT_ACTIVE),

			        (requestedUser, isActive, passwordEqual) -> passwordEqual.booleanValue()
			                ? securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.OLD_NEW_PASSWORD_MATCH)
			                : Mono.just(true)

			);
		}

		return flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(ca.isSystemClient()),

		        (ca, isSys) -> isSys.booleanValue() ? Mono.justOrEmpty(Optional.empty())
		                : this.dao.readById(reqUserId)
		                        .map(User::getClientId),
		        (ca, isSys, rClientId) ->
				{

			        if (isSys.booleanValue())
				        return Mono.just(true);

			        return Mono.just(rClientId.toBigInteger()
			                .equals(ca.getUser()
			                        .getClientId()));
		        },

		        (ca, isSys, rClientId, isSameClient) ->
				{

			        if (isSameClient.booleanValue())
				        return Mono.just(true);

			        return this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
			                .getClientId()), rClientId);
		        },

		        (ca, isSys, rclientId, isSameClient, isManaged) ->
				{

			        if (!isManaged.booleanValue())
				        return Mono.just(false);

			        return SecurityContextUtil.hasAuthority("Authorities.User_UPDATE");
		        }

		);
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

		        pastPasswords ->
				{

			        for (var pastPassword : pastPasswords) {
				        if ((pastPassword.isPasswordHashed()
				                && passwordEncoder.matches(pastPassword.getPassword(), user.getId() + newPassword))
				                || (!pastPassword.isPasswordHashed() && pastPassword.getPassword()
				                        .equals(newPassword)))
					        return this.securityMessageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
					                SecurityMessageResourceService.PASSWORD_USER_ERROR);

			        }

			        return Mono.just(true);
		        });

	}

}
