package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class UserService extends AbstractJOOQUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientPasswordPolicyService clientPasswordPolicyService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	private SoxLogService soxLogService;

	public Mono<User> findByClientIdsUserName(ULong clientId, String userName,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		return this.dao.getBy(clientId, userName, authenticationIdentifierType)
		        .flatMap(this.dao::setPermissions);
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
		        .flatMap(u -> this.setPassword(u, password))
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
		this.getLoggedInUserId()
		        .flatMap(e -> this.dao.setPassword(u.getId(), password, e))
		        .subscribe();
		return Mono.just(u);
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
		        .flatMap(e -> super.update(key, fields));
	}

	@Override
	public Mono<User> update(User entity) {

		return this.dao
		        .checkAvailability(entity.getId(), entity.getUserName(), entity.getEmailId(), entity.getPhoneNumber())
		        .flatMap(e -> super.update(entity));
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
		        .map(e -> 1);
	}

	public Mono<User> readInternal(ULong id) {
		return this.dao.readInternal(id)
		        .flatMap(this.dao::setPermissions);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> removePermissionFromUser(ULong userId, ULong permissionId) {
		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(userId),

		        (ca, user) ->

				clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
				        .getClientId()), user.getClientId()),

		        (ca, user, isManaged) ->
				{

			        if (ca.isSystemClient() || isManaged.booleanValue())

				        return this.dao.removingPermissionFromUser(userId, permissionId)
				                .map(val -> val > 0)
				                .filter(Boolean::booleanValue);

			        return Mono.empty();

		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.REMOVE_PERMISSION_ERROR, permissionId, userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User')")
	public Mono<Boolean> removeRoleFromUser(ULong userId, ULong roleId) {
		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(userId),

		        (ca, user) -> clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                .getClientId()), user.getClientId()),

		        (ca, user, isManaged) ->
				{
			        if (ca.isSystemClient() || isManaged.booleanValue())

				        return this.dao.removeRoleForUser(userId, roleId)
				                .map(val -> val > 0);

			        return Mono.empty();
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_User')")
	public Mono<Boolean> assignPermissionToUser(ULong userId, ULong permissionId) {
		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        contextAuth -> this.dao.readById(userId),

		        (contextAuth, user) ->
				{

			        if (contextAuth.isSystemClient())
				        return Mono.just(true);

			        return clientService.isBeingManagedBy(ULongUtil.valueOf(contextAuth.getUser()
			                .getClientId()), user.getClientId())
			                .flatMap(e -> e.booleanValue() ? Mono.just(e) : Mono.empty());
		        },

		        (contextAuth, user, isManaged) ->

				isManaged.booleanValue() ? flatMapMono(

				        () -> clientService.checkPermissionAvailableForGivenClient(user.getClientId(), permissionId),

				        havePermission -> this.dao.checkPermissionExistsForUser(user.getClientId(), permissionId),

				        (havePermission, permissionCreated) -> Mono.just(havePermission && permissionCreated)

				) : Mono.empty(),

		        (contextAuth, user, isManaged, hasPermission) ->
				{

			        if (((Boolean) hasPermission).booleanValue())

				        return this.dao.assigningPermissionToUser(userId, permissionId)
				                .map(val -> val > 0);

			        return Mono.empty();
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR, permissionId, userId));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_User')")
	public Mono<Boolean> assignRoleToUser(ULong userId, ULong roleId) {
		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(userId),

		        (ca, user) ->
				{

			        if (ca.isSystemClient())
				        return Mono.just(true);

			        return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                .getClientId()), user.getClientId())
			                .flatMap(e -> e.booleanValue() ? Mono.just(e) : Mono.empty());

		        },

		        (ca, user, sysOrManaged) ->

				sysOrManaged.booleanValue() ?

				        flatMapMono(

				                () -> this.clientService.checkRoleApplicableForSelectedClient(user.getClientId(),
				                        roleId),

				                roleApplicable -> this.dao.checkRoleCreatedByUser(userId, roleId),

				                (roleApplicable, roleCreated) -> Mono
				                        .just(roleApplicable.booleanValue() || roleCreated.booleanValue())

						) : Mono.empty(),

		        (ca, user, sysOrManaged, roleApplicable) ->

				{

			        if (((Boolean) roleApplicable).booleanValue())
				        return this.dao.checkRoleCreatedByUser(userId, roleId);

			        return Mono.empty();
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_FORBIDDEN, roleId, userId));
	}

	public Mono<Boolean> isBeingManagedBy(ULong loggedInClientId, ULong userId) {
		return this.dao.isBeingManagedBy(loggedInClientId, userId);
	}

	public Mono<Boolean> checkRoleCreatedByUser(ULong userId, ULong roleId) {
		return this.dao.checkRoleCreatedByUser(roleId, userId);
	}

	public Mono<Set<ULong>> getUserListFromClients(Set<ULong> clientList) {
		return this.dao.getUserListFromClientIds(clientList);
	}

	public Mono<Boolean> removingPermissionFromUser(ULong userId, ULong permissionId) {
		return this.dao.removingPermissionFromUser(userId, permissionId)
		        .map(val -> val > 0);
	}

	public Mono<Boolean> checkPasswordEqual(ULong userId, String newPassword) {
		return this.dao.checkPasswordEqual(userId, newPassword);
	}

	public Mono<Boolean> updateNewPassword(ULong reqUserId, RequestUpdatePassword requestPassword) {

		Mono<ContextUser> ca = SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser);

		Mono<ULong> loggedInUserId = ca.map(ContextUser::getId)
		        .map(ULong::valueOf);

		Mono<ULong> reqClientId = this.dao.readById(reqUserId)
		        .map(User::getClientId);

		// reset password only for active users

		return flatMapMono(

		        () -> loggedInUserId,

		        userId -> checkHierarchy(userId, reqUserId, requestPassword.getNewPassword()),

		        (userId, isUpdatable) -> isUpdatable.booleanValue() ? reqClientId : Mono.empty(),

		        (userId, isUpdatable, clientId) -> clientId != null
		                ? this.clientPasswordPolicyService.getPolicyByClientId(clientId)
		                : Mono.empty(),

		        (userId, isUpdatable, clientId, passwordPolicy) -> passwordPolicy != null
		                ? this.clientPasswordPolicyService.checkAllConditions(clientId,
		                        requestPassword.getNewPassword())
		                : Mono.just(true),

		        (userId, isUpdatable, clientId, passwordPolicy, isValid) ->

				isValid.booleanValue()
				        ? this.addBasedOnHistoryCount(reqUserId, requestPassword.getNewPassword(), userId,
				                passwordPolicy)
				        : Mono.empty(),

		        (userId, isUpdatable, clientId, passwordPolicy, isValid,
		                addPastPassword) -> addPastPassword.booleanValue()
		                        ? this.dao.updatePassword(reqUserId, requestPassword.getNewPassword())
		                        : Mono.empty(),

		        (userId, isUpdatable, clientId, passwordPolicy, isValid, addPastPassword, passwordUpdated) ->
				{

			        if (passwordUpdated.booleanValue()) {
				        this.soxLogService.create(new SoxLog().setObjectId(userId)
				                .setActionName(SecuritySoxLogActionName.OTHER)
				                .setObjectName(SecuritySoxLogObjectName.USER)
				                .setDescription("Password updated"))
				                .subscribe();

				        this.dao.makeUserActive(reqUserId)
				                .subscribe(); // making user active if it is another state

				        return Mono.just(true);
			        }

			        return Mono.empty();

		        }

		).log()

		        .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                "Password cannot be updated"));

	}

	// add past password check here with dao and service here

	public Mono<UShort> getHistoryCount(ULong clientId) {

		return flatMapMono(

		        () -> clientService.getClientPasswordPolicy(clientId),

		        passwordPolicy -> passwordPolicy != null && passwordPolicy.getPassHistoryCount() != null
		                ? Mono.just(passwordPolicy.getPassHistoryCount())
		                : Mono.just(UShort.valueOf(0)));

	}	

	// add new password based on history count which was applicable for that
	// selected user id

	public Mono<Boolean> addBasedOnHistoryCount(ULong reqUserId, String password, ULong lUserId,
	        ClientPasswordPolicy passwordPolicy) {

		return flatMapMono(

		        () -> Mono.just(reqUserId),

		        userId -> this.dao.fetchPasswordsCount(userId),

		        (userId, pastPasswordCount) -> passwordPolicy.getPassHistoryCount() != null
		                ? Mono.just(passwordPolicy.getPassHistoryCount())
		                : Mono.just(0),

		        (userId, pastPasswordCount, historyCount) ->
				{

			        if (historyCount != null && pastPasswordCount >= historyCount.intValue() && pastPasswordCount > 0) {
				        deletePastPassword(userId).subscribe();
			        }

			        this.dao.addPastPassword(reqUserId, password, lUserId)
			                .subscribe();
			        return Mono.just(true);

		        }

		).log();

	}

	// delete past password record based on oldest time stamp for selected userId
	public Mono<Boolean> deletePastPassword(ULong userId) {

		return this.dao.deletePastPasswordBasedOnUserId(userId);
	}

	// check the hierarchy with client id and user id as per the comment given
	// 1.) same user id
	// 2.) logged in user's client id matches given user's client id if logged in
	// user has user edit access update the password
	// 3.) logged in user's client id is system if logged in user has user edit
	// access update the password
	// 4.) if no client id matches check logged in user's client id is managing user
	// id's client id if logged in user has user edit access the update password

	public Mono<Boolean> checkHierarchy(ULong userId, ULong reqUserId, String newPassword) {

		if (newPassword == null)
			return securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			        SecurityMessageResourceService.NEW_PASSWORD_MISSING);

		if (userId != null && reqUserId != null && userId.equals(reqUserId)) {
			// also check password of old and new password
			// check user active return only if it is

			return flatMapMono(

			        () -> this.dao.checkUserActive(reqUserId),

			        isActive -> isActive.booleanValue() ? this.dao.checkPasswordEqual(reqUserId, newPassword)
			                : securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.USER_NOT_ACTIVE),

			        (isActive, passwordEqual) -> !passwordEqual.booleanValue() ? Mono.just(true)
			                : securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                        SecurityMessageResourceService.OLD_NEW_PASSWORD_MATCH)

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

}
