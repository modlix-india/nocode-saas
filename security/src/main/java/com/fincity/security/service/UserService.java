package com.fincity.security.service;

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
import com.fincity.nocode.reactor.util.FlatMapUtil;
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

	@PreAuthorize("hasAuthority('Authorities.Assign_Permission_To_User')")
	public Mono<Boolean> removePermissionFromUser(ULong userId, ULong permissionId) {
		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        contextAuth -> Mono
		                .just(ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(contextAuth.getClientTypeCode())),

		        (contextAuth, isSystem) -> this.dao.readById(userId),

		        (contextAuth, isSystem, user) -> clientService.isBeingManagedBy(ULongUtil.valueOf(contextAuth.getUser()
		                .getClientId()), user.getClientId()),

		        (contextAuth, isSystem, user, isManaged) ->
				{

			        if (isSystem.booleanValue() || isManaged.booleanValue())
				        return this.dao.removingPermissionFromUser(userId, permissionId);

			        return Mono.empty();

		        }

		)
		        .switchIfEmpty(Mono.defer(

		                () -> messageResourceService.getMessage(MessageResourceService.REMOVE_PERMISSION_ERROR)
		                        .map(msg -> new GenericException(HttpStatus.FORBIDDEN,
		                                StringFormatter.format(msg, permissionId, userId)))
		                        .flatMap(Mono::error)

				));
	}
}
