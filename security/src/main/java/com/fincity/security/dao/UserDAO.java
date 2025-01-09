package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;
import static com.fincity.security.jooq.tables.SecurityPastPins.SECURITY_PAST_PINS;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.SelectOrderByStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserDAO extends AbstractClientCheckDAO<SecurityUserRecord, ULong, User> {

	@Autowired
	private PasswordEncoder encoder;

	protected UserDAO() {
		super(User.class, SECURITY_USER, SECURITY_USER.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_USER.CLIENT_ID;
	}

	public Mono<ULong> getUserClientId(ULong userId) {
		return Mono.from(this.dslContext.select(SECURITY_USER.CLIENT_ID)
				.from(SECURITY_USER).where(SECURITY_USER.ID.eq(userId))).map(Record1::value1);
	}

	public Mono<Short> increaseFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
		return switch (passwordType) {
			case PASSWORD -> increaseFailedAttempt(userId);
			case PIN -> increasePinFailedAttempt(userId);
			case OTP -> increaseOtpFailedAttempt(userId);
		};
	}

	public Mono<Short> increaseResendAttempts(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_OTP_RESEND_ATTEMPT, SECURITY_USER.NO_OTP_RESEND_ATTEMPT.add(1))
				.where(SECURITY_USER.ID.eq(userId)))
				.flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_OTP_RESEND_ATTEMPT)
						.from(SECURITY_USER)
						.where(SECURITY_USER.ID.eq(userId)))
						.map(Record1::value1));
	}

	public Mono<Boolean> resetFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
		return switch (passwordType) {
			case PASSWORD -> resetFailedAttempt(userId);
			case PIN -> resetPinFailedAttempt(userId);
			case OTP -> resetOtpFailedAttempt(userId);
		};
	}

	public Mono<Boolean> resetResendAttempts(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_OTP_RESEND_ATTEMPT, (short) 0)
				.where(SECURITY_USER.ID.eq(userId)))
				.map(isUpdated -> isUpdated > 0);
	}

	private Mono<Short> increaseFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_FAILED_ATTEMPT, SECURITY_USER.NO_FAILED_ATTEMPT.add(1))
				.where(SECURITY_USER.ID.eq(userId)))
				.flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_FAILED_ATTEMPT)
						.from(SECURITY_USER)
						.where(SECURITY_USER.ID.eq(userId)))
						.map(Record1::value1));
	}

	private Mono<Short> increasePinFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_PIN_FAILED_ATTEMPT, SECURITY_USER.NO_PIN_FAILED_ATTEMPT.add(1))
				.where(SECURITY_USER.ID.eq(userId)))
				.flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_PIN_FAILED_ATTEMPT)
						.from(SECURITY_USER)
						.where(SECURITY_USER.ID.eq(userId)))
						.map(Record1::value1));
	}

	private Mono<Short> increaseOtpFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_OTP_FAILED_ATTEMPT, SECURITY_USER.NO_OTP_FAILED_ATTEMPT.add(1))
				.where(SECURITY_USER.ID.eq(userId)))
				.flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_OTP_FAILED_ATTEMPT)
						.from(SECURITY_USER)
						.where(SECURITY_USER.ID.eq(userId)))
						.map(Record1::value1));
	}

	private Mono<Boolean> resetFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_FAILED_ATTEMPT, (short) 0)
				.where(SECURITY_USER.ID.eq(userId)))
				.map(isUpdated -> isUpdated > 0);
	}

	private Mono<Boolean> resetPinFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_PIN_FAILED_ATTEMPT, (short) 0)
				.where(SECURITY_USER.ID.eq(userId)))
				.map(isUpdated -> isUpdated > 0);
	}

	private Mono<Boolean> resetOtpFailedAttempt(ULong userId) {
		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.NO_OTP_FAILED_ATTEMPT, (short) 0)
				.where(SECURITY_USER.ID.eq(userId)))
				.map(isUpdated -> isUpdated > 0);
	}

	public Mono<User> setPermissions(User user) {

		SelectOrderByStep<Record3<String, String, String>> query = this.dslContext
				.select(SECURITY_CLIENT.CODE, SECURITY_PERMISSION.NAME, SECURITY_APP.APP_CODE)
				.from(SECURITY_USER_ROLE_PERMISSION)
				.join(SECURITY_ROLE)
				.on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID))
				.join(SECURITY_ROLE_PERMISSION)
				.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_ROLE.ID))
				.join(SECURITY_PERMISSION)
				.on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))
				.join(SECURITY_CLIENT)
				.on(SECURITY_CLIENT.ID.eq(SECURITY_PERMISSION.CLIENT_ID))
				.leftJoin(SECURITY_APP)
				.on(SECURITY_APP.ID.eq(SECURITY_PERMISSION.APP_ID))
				.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId()))
				.union(this.dslContext.select(SECURITY_CLIENT.CODE, SECURITY_PERMISSION.NAME, SECURITY_APP.APP_CODE)
						.from(SECURITY_USER_ROLE_PERMISSION)
						.join(SECURITY_PERMISSION)
						.on(SECURITY_PERMISSION.ID.eq(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID))
						.join(SECURITY_CLIENT)
						.on(SECURITY_CLIENT.ID.eq(SECURITY_PERMISSION.CLIENT_ID))
						.leftJoin(SECURITY_APP)
						.on(SECURITY_APP.ID.eq(SECURITY_PERMISSION.APP_ID))
						.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId())))
				.union(this.dslContext
						.select(SECURITY_CLIENT.CODE, DSL.concat("ROLE_", SECURITY_ROLE.NAME), SECURITY_APP.APP_CODE)
						.from(SECURITY_USER_ROLE_PERMISSION)
						.join(SECURITY_ROLE)
						.on(SECURITY_ROLE.ID.eq(SECURITY_USER_ROLE_PERMISSION.ROLE_ID))
						.join(SECURITY_CLIENT)
						.on(SECURITY_CLIENT.ID.eq(SECURITY_ROLE.CLIENT_ID))
						.leftJoin(SECURITY_APP)
						.on(SECURITY_APP.ID.eq(SECURITY_ROLE.APP_ID))
						.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(user.getId())));

		return Flux.from(query)
				.map(r -> (r.value3() == null ? ""
						: r.value3()
								.toUpperCase() + ".")
						+ r.value2())
				.map(r -> "Authorities." + r.replace(' ', '_'))
				.collectList()
				.map(e -> {
					List<String> auths = new ArrayList<>(e);
					auths.add("Authorities.Logged_IN");
					return auths;
				})
				.map(user::setAuthorities);
	}

	public Mono<Boolean> checkUserExistsExclude(ULong clientId, String userName, String emailId, String phoneNumber,
			String typeCode, ULong userIdToExclude) {

		List<Condition> conditions = new ArrayList<>();

		if (typeCode != null)
			conditions.add(SECURITY_CLIENT.TYPE_CODE.in(typeCode));

		if (userIdToExclude != null)
			conditions.add(SECURITY_USER.ID.ne(userIdToExclude));

		return this.checkUserExists(clientId, userName, emailId, phoneNumber, conditions);
	}

	public Mono<Boolean> checkUserExists(ULong clientId, String userName, String emailId, String phoneNumber,
			String typeCode) {

		List<Condition> conditions = new ArrayList<>();

		if (typeCode != null)
			conditions.add(SECURITY_CLIENT.TYPE_CODE.in(typeCode));

		return this.checkUserExists(clientId, userName, emailId, phoneNumber, conditions);
	}

	private Mono<Boolean> checkUserExists(ULong clientId, String userName, String emailId, String phoneNumber,
			List<Condition> extraConditions) {

		List<Condition> userConditions = new ArrayList<>(extraConditions);

		if (clientId == null)
			return messageResourceService
					.getMessage(SecurityMessageResourceService.PARAMS_NOT_FOUND, "clientId, userId", "checkUserExists")
					.flatMap(msg -> Mono.error(new GenericException(HttpStatus.CONFLICT, msg)));

		userConditions.add(SECURITY_USER.CLIENT_ID.eq(clientId));

		userConditions.add(DSL.or(getUserAvailabilityConditions(userName, emailId, phoneNumber)));

		userConditions.add(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED));

		return Mono.from(
				this.dslContext.selectCount().from(SECURITY_USER)
						.leftJoin(SECURITY_CLIENT).on(SECURITY_CLIENT.ID.eq(SECURITY_USER.CLIENT_ID))
						.where(DSL.and(userConditions)))
				.map(e -> e.value1() > 0);
	}

	private List<Condition> getUserAvailabilityConditions(String userName, String emailId, String phoneNumber) {

		List<Condition> conditions = new ArrayList<>();

		if (!StringUtil.safeIsBlank(userName) && !User.PLACEHOLDER.equals(userName))
			conditions.add(SECURITY_USER.USER_NAME.eq(userName));

		if (!StringUtil.safeIsBlank(emailId) && !User.PLACEHOLDER.equals(emailId))
			conditions.add(SECURITY_USER.EMAIL_ID.eq(emailId));

		if (!StringUtil.safeIsBlank(phoneNumber) && !User.PLACEHOLDER.equals(phoneNumber))
			conditions.add(SECURITY_USER.PHONE_NUMBER.eq(phoneNumber));

		return conditions;
	}

	public Mono<Integer> setPassword(ULong userId, ULong currentUserId, String password,
			AuthenticationPasswordType passwordType) {

		String encryptedPassword = encoder.encode((userId + password));

		return switch (passwordType) {
			case PASSWORD -> setPassword(userId, currentUserId, encryptedPassword);
			case PIN -> setPin(userId, currentUserId, encryptedPassword);
			default -> Mono.just(0);
		};
	}

	private Mono<Integer> setPassword(ULong userId, ULong currentUserId, String encryptedPassword) {

		Mono.from(this.dslContext
				.insertInto(SECURITY_PAST_PASSWORDS, SECURITY_PAST_PASSWORDS.USER_ID, SECURITY_PAST_PASSWORDS.PASSWORD,
						SECURITY_PAST_PASSWORDS.PASSWORD_HASHED, SECURITY_PAST_PASSWORDS.CREATED_BY)
				.values(userId, encryptedPassword, ByteUtil.ONE, currentUserId))
				.subscribe();

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.PASSWORD, encryptedPassword)
				.set(SECURITY_USER.PASSWORD_HASHED, ByteUtil.ONE)
				.set(SECURITY_USER.UPDATED_BY, currentUserId)
				.where(SECURITY_USER.ID.eq(userId)));
	}

	private Mono<Integer> setPin(ULong userId, ULong currentUserId, String encryptedPin) {

		Mono.from(this.dslContext
				.insertInto(SECURITY_PAST_PINS, SECURITY_PAST_PINS.USER_ID, SECURITY_PAST_PINS.PIN,
						SECURITY_PAST_PINS.PIN_HASHED, SECURITY_PAST_PINS.CREATED_BY)
				.values(userId, encryptedPin, ByteUtil.ONE, currentUserId))
				.subscribe();

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.PIN, encryptedPin)
				.set(SECURITY_USER.PIN_HASHED, ByteUtil.ONE)
				.set(SECURITY_USER.UPDATED_BY, currentUserId)
				.where(SECURITY_USER.ID.eq(userId)));
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

	public Mono<Integer> removePermissionFromUser(ULong userId, ULong permissionId) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
				.deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
				.and(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)));

		return Mono.from(query);
	}

	public Mono<Boolean> removePermissionListFromUser(List<ULong> userList, List<ULong> permissionList) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
				.deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(userList)
				.and(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.in(permissionList)));

		return Mono.from(query)
				.map(count -> count > 0);
	}

	public Mono<Boolean> assignPermissionToUser(ULong userId, ULong permissionId) {

		return Mono.from(
				this.dslContext
						.insertInto(SECURITY_USER_ROLE_PERMISSION, SECURITY_USER_ROLE_PERMISSION.USER_ID,
								SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)
						.values(userId, permissionId))
				.map(value -> value > 0);
	}

	public Mono<Boolean> checkPermissionAssignedForUser(ULong userId, ULong permissionId) {

		return Mono.from(
				this.dslContext.selectCount()
						.from(SECURITY_USER_ROLE_PERMISSION)
						.where(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)
								.and(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)))

		)
				.map(Record1::value1)
				.map(count -> count == 1);
	}

	public Mono<Boolean> checkRoleAssignedForUser(ULong userId, ULong roleId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_USER_ROLE_PERMISSION)
						.where(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.eq(roleId)
								.and(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)))

		)
				.map(Record1::value1)
				.map(count -> count == 1);
	}

	public Mono<Boolean> addRoleToUser(ULong userId, ULong roleId) {

		return Mono.from(

				this.dslContext
						.insertInto(SECURITY_USER_ROLE_PERMISSION, SECURITY_USER_ROLE_PERMISSION.USER_ID,
								SECURITY_USER_ROLE_PERMISSION.ROLE_ID)
						.values(userId, roleId))
				.map(value -> value > 0);
	}

	public Mono<List<User>> getBy(String userName, ULong userId, String clientCode, String appCode,
			AuthenticationIdentifierType authenticationIdentifierType, SecurityUserStatusCode... userStatusCodes) {

		SelectConditionStep<Record> query = getAllUsersPerAppQuery(userName, userId, clientCode, appCode,
				authenticationIdentifierType, userStatusCodes, SECURITY_USER.fields());

		SelectLimitPercentStep<Record> limitQuery = query.limit(2);

		return Mono.just(limitQuery)
				.flatMap(LogUtil.logIfDebugKey(logger, "User Query : {}", limitQuery.toString()))
				.flatMapMany(Flux::from)
				.map(e -> e.into(User.class))
				.collectList();
	}

	private SelectConditionStep<Record> getAllUsersPerAppQuery(String userName, ULong userId, String clientCode,
			String appCode, AuthenticationIdentifierType authenticationIdentifierType,
			SecurityUserStatusCode[] userStatusCodes, Field<?>... fields) {

		TableField<SecurityUserRecord, String> userIdentificationField;

		switch (authenticationIdentifierType) {
			case PHONE_NUMBER -> userIdentificationField = SECURITY_USER.PHONE_NUMBER;
			case EMAIL_ID -> userIdentificationField = SECURITY_USER.EMAIL_ID;
			default -> userIdentificationField = SECURITY_USER.USER_NAME;
		}

		List<Condition> conditions = new ArrayList<>();

		if (!StringUtil.safeIsBlank(userName) && !User.PLACEHOLDER.equals(userName))
			conditions.add(userIdentificationField.eq(userName));

		if (userStatusCodes != null && userStatusCodes.length > 0)
			conditions.add(SECURITY_USER.STATUS_CODE.in(userStatusCodes));

		if (userId != null)
			conditions.add(SECURITY_USER.ID.eq(userId));

		conditions.add(
				SECURITY_APP.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID)
						.or(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID)));

		conditions.add(
				SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.CLIENT_ID)
						.or(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_0))
						.or(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_1))
						.or(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_2))
						.or(SECURITY_USER.CLIENT_ID.eq(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_3)));

		return this.dslContext.select(fields).from(SECURITY_USER)
				.leftJoin(SECURITY_APP).on(SECURITY_APP.APP_CODE.eq(appCode))
				.leftJoin(SECURITY_APP_ACCESS)
				.on(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID)
						.and(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)))
				.leftJoin(SECURITY_CLIENT).on(SECURITY_CLIENT.CODE.eq(clientCode))
				.leftJoin(SECURITY_CLIENT_HIERARCHY).on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
				.where(DSL.and(conditions));
	}

	public Mono<Map<ULong, ULong>> getAllClientsBy(String userName, String clientCode, String appCode,
			AuthenticationIdentifierType identifierType, SecurityUserStatusCode... userStatusCodes) {

		return Flux
				.from(this.getAllUsersPerAppQuery(userName, null, clientCode, appCode, identifierType,
						userStatusCodes, SECURITY_USER.ID, SECURITY_USER.CLIENT_ID))
				.collectMap(e -> e.getValue(SECURITY_USER.ID), e -> e.getValue(SECURITY_USER.CLIENT_ID));
	}

	public Mono<Boolean> lockUser(ULong userId, LocalDateTime lockUntil, String lockedDueTo) {

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.LOCKED)
				.set(SECURITY_USER.ACCOUNT_NON_LOCKED, ByteUtil.ZERO)
				.set(SECURITY_USER.LOCKED_UNTIL, lockUntil)
				.set(SECURITY_USER.LOCKED_DUE_TO, lockedDueTo)
				.where(SECURITY_USER.ID.eq(userId)))
				.map(e -> e > 0);
	}

	public Mono<Boolean> makeUserActiveIfInActive(ULong uid) {

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.ACTIVE)
				.where(SECURITY_USER.ID.eq(uid)
						.and(SECURITY_USER.STATUS_CODE.eq(SecurityUserStatusCode.INACTIVE))))
				.map(e -> e > 0);

	}

	public Mono<Boolean> makeUserInActive(ULong id) {

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.INACTIVE)
				.where(SECURITY_USER.ID.eq(id)
						.and(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED))))
				.map(e -> e > 0);

	}

	public Mono<Boolean> updateUserStatusToActive(ULong reqUserId) {

		return Mono.from(this.dslContext.update(SECURITY_USER)
				.set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.ACTIVE)
				.set(SECURITY_USER.ACCOUNT_NON_EXPIRED, ByteUtil.ONE)
				.set(SECURITY_USER.ACCOUNT_NON_LOCKED, ByteUtil.ONE)
				.set(SECURITY_USER.CREDENTIALS_NON_EXPIRED, ByteUtil.ONE)
				.set(SECURITY_USER.NO_FAILED_ATTEMPT, (short) 0)
				.set(SECURITY_USER.NO_PIN_FAILED_ATTEMPT, (short) 0)
				.set(SECURITY_USER.NO_OTP_RESEND_ATTEMPT, (short) 0)
				.set(SECURITY_USER.NO_OTP_FAILED_ATTEMPT, (short) 0)
				.set(SECURITY_USER.LOCKED_UNTIL, (LocalDateTime) null)
				.set(SECURITY_USER.LOCKED_DUE_TO, (String) null)
				.where(SECURITY_USER.ID.eq(reqUserId)
						.and(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED))))
				.map(e -> e > 0);
	}

	public Mono<List<Permission>> fetchPermissionsFromGivenUser(ULong userId) {

		return Flux.from(
				this.dslContext.select(SECURITY_PERMISSION.fields())
						.from(SECURITY_PERMISSION)
						.where(
								SECURITY_PERMISSION.ID.in(
										this.dslContext.select(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)
												.from(SECURITY_USER_ROLE_PERMISSION)
												.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
														.and(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID
																.isNotNull())))))
				.map(e -> e.into(Permission.class))
				.collectList();
	}

	public Mono<List<Role>> fetchRolesFromGivenUser(ULong userId) {

		return Flux.from(
				this.dslContext.select(SECURITY_ROLE.fields())
						.from(SECURITY_ROLE)
						.where(
								SECURITY_ROLE.ID.in(
										this.dslContext.select(SECURITY_USER_ROLE_PERMISSION.ROLE_ID)
												.from(SECURITY_USER_ROLE_PERMISSION)
												.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId)
														.and(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.isNotNull())))))
				.map(e -> e.into(Role.class))
				.collectList();
	}

	public Mono<Boolean> copyRolesNPermissionsFromUser(ULong userId, ULong referenceUserId) {

		return Mono.from(
				this.dslContext
						.insertInto(SECURITY_USER_ROLE_PERMISSION,
								SECURITY_USER_ROLE_PERMISSION.USER_ID,
								SECURITY_USER_ROLE_PERMISSION.ROLE_ID,
								SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)
						.select(
								this.dslContext.select(
										DSL.val(userId),
										SECURITY_USER_ROLE_PERMISSION.ROLE_ID,
										SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID)
										.from(SECURITY_USER_ROLE_PERMISSION)
										.where(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(referenceUserId)))
						.onDuplicateKeyIgnore())
				.map(rowsInserted -> rowsInserted > 0);
	}

}
