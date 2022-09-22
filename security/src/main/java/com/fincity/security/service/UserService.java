package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
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
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class UserService extends AbstractJOOQUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

	@Autowired
	private ClientService clientService;

	@Autowired
	private MessageResourceService messageResourceService;

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
		        .switchIfEmpty(Mono.defer(() -> messageResourceService
		                .getMessage(MessageResourceService.FORBIDDEN_CREATE)
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
				        return Mono.defer(
				                () -> messageResourceService.getMessage(MessageResourceService.FORBIDDEN_PERMISSION)
				                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
				                                StringFormatter.format(msg, "User READ")))));

			        return Mono.just(e);
		        }))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService
		                .getMessage(MessageResourceService.OBJECT_NOT_FOUND)
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

			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())
			                || isManaged.booleanValue())

				        return this.dao.removingPermissionFromUser(userId, permissionId)
				                .map(val -> val > 0)
				                .filter(Boolean::booleanValue);

			        return Mono.empty();

		        }

		).switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        MessageResourceService.REMOVE_PERMISSION_ERROR, permissionId, userId));
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
			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())
			                || isManaged.booleanValue())

				        return this.dao.removeRoleForUser(userId, roleId)
				                .map(val -> val > 0);

			        return Mono.empty();
		        }

		).switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        MessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
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
				{
			        System.out.println("managed 3 " + isManaged);
//			        if (isManaged.booleanValue())
//			        return this.dao.checkPermissionExistsForUser(userId, permissionId);

			        return Mono.just(false);
//			        else
//				        return Mono.empty();
		        },

		        (contextAuth, user, isManaged, exists) ->
				{
			        System.out.println("exists 4 " + exists);

			        return clientService.checkPermissionAvailableForGivenClient(user.getClientId(), permissionId);

		        },

		        (contextAuth, user, isManaged, exists, hasPermission) ->
				{
			        System.out.println("permission 5 " + hasPermission);

			        if (isManaged.booleanValue() && hasPermission.booleanValue() && !exists.booleanValue())

				        return this.dao.assigningPermissionToUser(userId, permissionId)
				                .map(res -> res > 0);

			        else if (exists.booleanValue())
				        return Mono.just(false);

			        return Mono.empty();
		        }).switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                MessageResourceService.ASSIGN_PERMISSION_ERROR, permissionId, userId));
	}
}
