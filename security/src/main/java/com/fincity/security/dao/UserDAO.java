package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.*;
import static com.fincity.security.jooq.tables.SecurityAppAccess.*;
import static com.fincity.security.jooq.tables.SecurityClient.*;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.*;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.*;
import static com.fincity.security.jooq.tables.SecurityPastPins.*;
import static com.fincity.security.jooq.tables.SecurityUser.*;
import static com.fincity.security.jooq.tables.SecurityV2Role.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileUser;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.SecurityV2RolePermission;
import com.fincity.security.jooq.tables.SecurityV2RoleRole;
import com.fincity.security.jooq.tables.SecurityV2UserRole;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.util.AuthoritiesNameUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

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

	private Mono<Boolean> checkUserExists(ULong managingClientId, String userName, String emailId, String phoneNumber,
			List<Condition> extraConditions) {

		List<Condition> userConditions = new ArrayList<>(extraConditions);

		if (managingClientId == null)
			return messageResourceService
					.getMessage(SecurityMessageResourceService.PARAMS_NOT_FOUND, "clientId, userId", "checkUserExists")
					.flatMap(msg -> Mono.error(new GenericException(HttpStatus.CONFLICT, msg)));

		userConditions.add(SECURITY_USER.CLIENT_ID.in(
				this.dslContext.select(SECURITY_CLIENT_HIERARCHY.CLIENT_ID)
						.from(SECURITY_CLIENT_HIERARCHY)
						.where(SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_0.eq(managingClientId))));

		userConditions.add(DSL.and(getUserAvailabilityConditions(userName, emailId, phoneNumber)));

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

		return Mono.from(this.dslContext.delete(SecurityV2UserRole.SECURITY_V2_USER_ROLE).where(
				SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID.eq(userId)
						.and(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID.eq(roleId))));
	}

	public Mono<Boolean> checkRoleAssignedForUser(ULong userId, ULong roleId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SecurityV2UserRole.SECURITY_V2_USER_ROLE)
						.where(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID.eq(roleId)
								.and(SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID.eq(userId)))

		)
				.map(Record1::value1)
				.map(count -> count == 1);
	}

	public Mono<Boolean> addRoleToUser(ULong userId, ULong roleId) {

		return Mono.from(

				this.dslContext
						.insertInto(SecurityV2UserRole.SECURITY_V2_USER_ROLE,
								SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID,
								SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID)
						.values(userId, roleId))
				.map(value -> value > 0);
	}

	public Mono<Integer> removeProfileForUser(ULong userId, ULong profileId) {

		return Mono.from(this.dslContext.delete(SecurityProfileUser.SECURITY_PROFILE_USER).where(
				SecurityProfileUser.SECURITY_PROFILE_USER.USER_ID.eq(userId)
						.and(SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID.eq(profileId))));
	}

	public Mono<List<User>> getUsersBy(String userName, ULong userId, String clientCode, String appCode,
			AuthenticationIdentifierType authenticationIdentifierType, SecurityUserStatusCode... userStatusCodes) {

		SelectConditionStep<Record> query = this.getAllUsersPerAppQuery(userName, userId, clientCode, appCode,
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

		conditions.add(ClientHierarchyDAO.getManageClientCondition(SECURITY_CLIENT.ID));

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

	public Mono<Integer> addProfileToUser(ULong userId, ULong profileId) {

		return Mono.from(this.dslContext
				.insertInto(SecurityProfileUser.SECURITY_PROFILE_USER,
						SecurityProfileUser.SECURITY_PROFILE_USER.USER_ID,
						SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID)
				.values(this.dslContext
						.select(DSL.val(userId).as("USER_ID"),
								DSL.coalesce(SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID,
										SecurityProfile.SECURITY_PROFILE.ID).as("PROFILE_ID"))
						.from(SecurityProfile.SECURITY_PROFILE)
						.where(SecurityProfile.SECURITY_PROFILE.ID.eq(profileId))
						.fetchOne())
				.onDuplicateKeyIgnore())
				.map(value -> value > 0 ? 1 : 0);
	}

	public Mono<List<String>> getRoleAuthorities(String appCode, ULong userId) {

		return FlatMapUtil.flatMapMono(

				() -> Flux.from(this.dslContext
						.select(SECURITY_APP.APP_CODE, SECURITY_V2_ROLE.NAME, SECURITY_V2_ROLE.ID)
						.from(SECURITY_V2_ROLE)
						.leftJoin(SECURITY_APP)
						.on(SECURITY_V2_ROLE.APP_ID.eq(SECURITY_APP.ID))
						.where(SECURITY_V2_ROLE.ID.in(
								this.dslContext
										.select(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID)
										.from(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE)
										.leftJoin(SecurityV2UserRole.SECURITY_V2_USER_ROLE)
										.on(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID
												.eq(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.ROLE_ID))
										.where(SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID.eq(userId))
										.union(
												this.dslContext
														.select(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID)
														.from(SecurityV2UserRole.SECURITY_V2_USER_ROLE)
														.where(SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID
																.eq(userId))))
								.and(SECURITY_APP.APP_CODE.eq(appCode).or(SECURITY_V2_ROLE.APP_ID.isNull()))))
						.collectList(),

				roles -> Flux
						.from(this.dslContext.select(SECURITY_APP.APP_CODE, SecurityPermission.SECURITY_PERMISSION.NAME)
								.from(SecurityPermission.SECURITY_PERMISSION)
								.leftJoin(SECURITY_APP)
								.on(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(SECURITY_APP.ID))
								.leftJoin(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION)
								.on(SecurityPermission.SECURITY_PERMISSION.ID
										.eq(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.PERMISSION_ID))
								.where(DSL.and(
										SECURITY_APP.APP_CODE.eq(appCode)
												.or(SecurityPermission.SECURITY_PERMISSION.APP_ID.isNull()),
										SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.ROLE_ID
												.in(roles.stream().map(Record3::value3).collect(Collectors.toList())))))
						.collectList(),

				(roles, permissions) -> Mono.just(

						Stream.concat(
								roles.stream()
										.map(e -> AuthoritiesNameUtil.makeName(
												e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
												e.getValue(SecurityV2Role.SECURITY_V2_ROLE.NAME), true)),
								permissions
										.stream()
										.map(e -> AuthoritiesNameUtil.makeName(
												e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
												e.getValue(SecurityPermission.SECURITY_PERMISSION.NAME), false)))
								.toList())

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserDAO.getRoleAuthorities"));
	}
}
