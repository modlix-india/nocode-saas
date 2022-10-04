package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectOrderByStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.util.ByteUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserDAO extends AbstractClientCheckDAO<SecurityUserRecord, ULong, User> {

	@Autowired
	private PasswordEncoder encoder;

	protected UserDAO() {
		super(User.class, SECURITY_USER, SECURITY_USER.ID);
	}

	public Mono<User> getBy(ULong clientId, String userName,
	        AuthenticationIdentifierType authenticationIdentifierType) {

		TableField<SecurityUserRecord, String> field = SECURITY_USER.USER_NAME;
		if (authenticationIdentifierType == AuthenticationIdentifierType.EMAIL_ID) {
			field = SECURITY_USER.EMAIL_ID;
		} else if (authenticationIdentifierType == AuthenticationIdentifierType.PHONE_NUMBER) {
			field = SECURITY_USER.PHONE_NUMBER;
		}

		return Mono.from(this.dslContext.select(SECURITY_USER.fields())
		        .from(SECURITY_USER)
		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		        .where(field.eq(userName)
		                .and(SECURITY_USER.CLIENT_ID.eq(clientId)
		                        .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(clientId))))
		        .limit(1))
		        .map(e -> e.into(User.class));
	}

	public Mono<Object> increaseFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.NO_FAILED_ATTEMPT, SECURITY_USER.NO_FAILED_ATTEMPT.add(1))
		        .where(SECURITY_USER.ID.eq(userId)));
	}

	public Mono<Object> resetFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.NO_FAILED_ATTEMPT, Short.valueOf((short) 0))
		        .where(SECURITY_USER.ID.eq(userId)));
	}

	public Mono<User> setPermissions(User user) {

		SelectOrderByStep<Record1<String>> query = this.dslContext.select(SECURITY_PERMISSION.NAME)
		        .from(SECURITY_USER_ROLE_PERMISSION)
		        .join(SECURITY_ROLE)
		        .on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID))
		        .join(SECURITY_ROLE_PERMISSION)
		        .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_ROLE.ID))
		        .join(SECURITY_PERMISSION)
		        .on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))
		        .where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId()))
		        .union(this.dslContext.select(SECURITY_PERMISSION.NAME)
		                .from(SECURITY_USER_ROLE_PERMISSION)
		                .join(SECURITY_PERMISSION)
		                .on(SECURITY_PERMISSION.ID.eq(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID))
		                .where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId())))
		        .union(this.dslContext.select(DSL.concat("ROLE_", SECURITY_ROLE.NAME))
		                .from(SECURITY_USER_ROLE_PERMISSION)
		                .join(SECURITY_ROLE)
		                .on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID))
		                .where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId())));

		return Flux.from(query)
		        .map(Record1::value1)
		        .collectList()
		        .map(e ->
				{
			        List<String> auths = new ArrayList<>(e);
			        auths.add("Logged IN");
			        return auths;
		        })
		        .map(user::setAuthorities);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_USER.CLIENT_ID;
	}

	public Mono<Boolean> checkAvailabilityWithClientId(ULong clientID, String userName, String emailId,
	        String phoneNumber) {

		Mono<Set<ULong>> clientIds = Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT)
		        .where(SECURITY_CLIENT.ID.eq(clientID))
		        .limit(1))
		        .map(e -> e.into(Client.class))
		        .flatMap(this::getClientIdsToCheckUserAvailability);

		List<Condition> conditions = getUserAvailabilityConditions(userName, emailId, phoneNumber);

		return clientIds
		        .flatMap(
		                cis -> Flux
		                        .from(this.dslContext
		                                .select(SECURITY_USER.USER_NAME, SECURITY_USER.EMAIL_ID,
		                                        SECURITY_USER.PHONE_NUMBER)
		                                .from(SECURITY_USER)
		                                .where(SECURITY_USER.CLIENT_ID.in(cis)
		                                        .and(DSL.or(conditions))))
		                        .collectList()
		                        .flatMap(e -> this.generateAvailabilityException(userName, emailId, phoneNumber, e))
		                        .switchIfEmpty(Mono.just(true)));
	}

	public Mono<Boolean> checkAvailability(ULong userId, String userName, String emailId, String phoneNumber) {

		Mono<Client> client = Mono.from(this.dslContext.select(SECURITY_CLIENT.fields())
		        .from(SECURITY_CLIENT)
		        .leftJoin(SECURITY_USER)
		        .on(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(SECURITY_USER.ID.eq(userId)
		                .and(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED)))
		        .limit(1))
		        .map(e -> e.into(Client.class));

		Mono<Set<ULong>> clientIds = client.flatMap(this::getClientIdsToCheckUserAvailability);

		List<Condition> conditions = getUserAvailabilityConditions(userName, emailId, phoneNumber);

		return clientIds.flatMap(cis -> Flux
		        .from(this.dslContext
		                .select(SECURITY_USER.USER_NAME, SECURITY_USER.EMAIL_ID, SECURITY_USER.PHONE_NUMBER)
		                .from(SECURITY_USER)
		                .where(SECURITY_USER.CLIENT_ID.in(cis)
		                        .and(SECURITY_USER.ID.ne(userId)
		                                .and(DSL.or(conditions)))))
		        .collectList()
		        .flatMap(e -> this.generateAvailabilityException(userName, emailId, phoneNumber, e))
		        .switchIfEmpty(Mono.just(true)));
	}

	public List<Condition> getUserAvailabilityConditions(String userName, String emailId, String phoneNumber) {

		List<Condition> conditions = new ArrayList<>();

		if (userName != null && !User.PLACEHOLDER.equals(userName))
			conditions.add(SECURITY_USER.USER_NAME.eq(userName));

		if (emailId != null && !User.PLACEHOLDER.equals(emailId))
			conditions.add(SECURITY_USER.EMAIL_ID.eq(emailId));

		if (phoneNumber != null && !User.PLACEHOLDER.equals(phoneNumber))
			conditions.add(SECURITY_USER.PHONE_NUMBER.eq(phoneNumber));
		return conditions;
	}

	private Mono<Boolean> generateAvailabilityException(String userName, String emailId, String phoneNumber,
	        List<Record3<String, String, String>> e) {

		for (Record3<String, String, String> r : e) {
			if (userName != null && !User.PLACEHOLDER.equals(userName) && r.value1()
			        .equals(userName)) {
				return messageResourceService.getMessage(SecurityMessageResourceService.ALREADY_EXISTS)
				        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.CONFLICT,
				                StringFormatter.format(msg, "User name", userName))));
			}

			if (emailId != null && !User.PLACEHOLDER.equals(emailId) && r.value2()
			        .equals(emailId)) {
				return messageResourceService.getMessage(SecurityMessageResourceService.ALREADY_EXISTS)
				        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.CONFLICT,
				                StringFormatter.format(msg, "Email", emailId))));
			}

			if (phoneNumber != null && !User.PLACEHOLDER.equals(phoneNumber) && r.value3()
			        .equals(phoneNumber)) {
				return messageResourceService.getMessage(SecurityMessageResourceService.ALREADY_EXISTS)
				        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.CONFLICT,
				                StringFormatter.format(msg, "Phone number", phoneNumber))));
			}
		}
		return Mono.just(false);
	}

	private Mono<Set<ULong>> getClientIdsToCheckUserAvailability(Client c) {

		if (c.getTypeCode()
		        .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
			return Mono.just(Set.of(c.getId()));

		return Flux
		        .from(this.dslContext.select(SECURITY_CLIENT_MANAGE.CLIENT_ID, SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID)
		                .from(SECURITY_CLIENT_MANAGE)
		                .where(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(c.getId())
		                        .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(c.getId()))))
		        .flatMap(e -> Flux.just(e.value1(), e.value2()))
		        .collectList()
		        .map(HashSet::new)
		        .map(e ->
				{
			        e.add(c.getId());
			        return e;
		        });

	}

	public Mono<Integer> setPassword(ULong id, String password, ULong currentUserId) {

		String encryptedPassword = encoder.encode((id + password));
		Mono.from(this.dslContext
		        .insertInto(SECURITY_PAST_PASSWORDS, SECURITY_PAST_PASSWORDS.USER_ID, SECURITY_PAST_PASSWORDS.PASSWORD,
		                SECURITY_PAST_PASSWORDS.PASSWORD_HASHED, SECURITY_PAST_PASSWORDS.CREATED_BY)
		        .values(id, encryptedPassword, ByteUtil.ONE, currentUserId))
		        .subscribe();

		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.PASSWORD, encryptedPassword)
		        .set(SECURITY_USER.PASSWORD_HASHED, ByteUtil.ONE)
		        .set(SECURITY_USER.UPDATED_BY, currentUserId)
		        .where(SECURITY_USER.ID.eq(id)));
	}

	public Mono<User> readInternal(ULong id) {
		return Mono.from(this.dslContext.selectFrom(this.table)
		        .where(this.idField.eq(id))
		        .limit(1))
		        .map(e -> e.into(this.pojoClass));
	}

	public Mono<Integer> removeRoleForUser(ULong userId, ULong roleId) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
		        .deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
		        .and(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.eq(roleId)));

		return Mono.from(query);
	}

	public Mono<Integer> removingPermissionFromUser(ULong userId, ULong permissionId) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
		        .deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
		        .and(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)));

		return Mono.from(query);
	}

	public Mono<Integer> assigningPermissionToUser(ULong userId, ULong permissionId) {

		return Mono.from(

		        this.dslContext
		                .insertInto(SECURITY_USER_ROLE_PERMISSION, SECURITY_USER_ROLE_PERMISSION.USER_ID,
		                        SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)
		                .values(userId, permissionId));
	}

	public Mono<Boolean> checkPermissionExistsForUser(ULong userId, ULong permissionId) {
		return Mono.from(

		        this.dslContext.select(SECURITY_USER_ROLE_PERMISSION.ID)
		                .from(SECURITY_USER_ROLE_PERMISSION)
		                .where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
		                        .and(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)))
		                .limit(1)

		)
		        .map(Record1::value1)
		        .map(val -> val.intValue() > 0);

	}
}
