package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;
import static com.fincity.security.jooq.tables.SecurityPastPins.SECURITY_PAST_PINS;
import static com.fincity.security.jooq.tables.SecurityProfile.SECURITY_PROFILE;
import static com.fincity.security.jooq.tables.SecurityProfileRole.SECURITY_PROFILE_ROLE;
import static com.fincity.security.jooq.tables.SecurityProfileUser.SECURITY_PROFILE_USER;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityV2Role.SECURITY_V2_ROLE;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileUser;
import com.fincity.security.jooq.tables.SecurityUser;
import com.fincity.security.jooq.tables.SecurityV2UserRole;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Component
public class UserDAO extends AbstractClientCheckDAO<SecurityUserRecord, ULong, User> {

    @Autowired
    private PasswordEncoder encoder;

    @Lazy
    @Autowired
    private ClientDAO clientDAO;

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
                        .set(SECURITY_USER.NO_FAILED_ATTEMPT, DSL.when(SECURITY_USER.NO_FAILED_ATTEMPT.isNull(), (short) 1)
                                .otherwise(SECURITY_USER.NO_FAILED_ATTEMPT.add(1)))
                        .where(SECURITY_USER.ID.eq(userId)))
                .flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_FAILED_ATTEMPT)
                                .from(SECURITY_USER)
                                .where(SECURITY_USER.ID.eq(userId)))
                        .map(Record1::value1));
    }

    private Mono<Short> increasePinFailedAttempt(ULong userId) {
        return Mono.from(this.dslContext.update(SECURITY_USER)
                        .set(SECURITY_USER.NO_PIN_FAILED_ATTEMPT, DSL.when(SECURITY_USER.NO_PIN_FAILED_ATTEMPT.isNull(), (short) 1)
                                .otherwise(SECURITY_USER.NO_PIN_FAILED_ATTEMPT.add(1)))
                        .where(SECURITY_USER.ID.eq(userId)))
                .flatMap(updatedRows -> Mono.from(this.dslContext.select(SECURITY_USER.NO_PIN_FAILED_ATTEMPT)
                                .from(SECURITY_USER)
                                .where(SECURITY_USER.ID.eq(userId)))
                        .map(Record1::value1));
    }

    private Mono<Short> increaseOtpFailedAttempt(ULong userId) {
        return Mono.from(this.dslContext.update(SECURITY_USER)
                        .set(SECURITY_USER.NO_OTP_FAILED_ATTEMPT, DSL.when(SECURITY_USER.NO_OTP_FAILED_ATTEMPT.isNull(), (short) 1)
                                .otherwise(SECURITY_USER.NO_OTP_FAILED_ATTEMPT.add(1)))
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

    public Mono<Boolean> checkUserExists(ULong managingClientId, String userName, String emailId, String phoneNumber,
                                         String typeCode) {

        List<Condition> conditions = new ArrayList<>();

        if (typeCode != null)
            conditions.add(SECURITY_CLIENT.TYPE_CODE.in(typeCode));

        return this.checkUserExists(managingClientId, userName, emailId, phoneNumber, conditions);
    }

    public Mono<Boolean> checkUserExistsForInvite(ULong clientId, String userName, String emailId, String phoneNumber) {

        List<Condition> conditions = new ArrayList<>();

        if (!StringUtil.safeIsBlank(userName) && !User.PLACEHOLDER.equals(userName))
            conditions.add(SECURITY_USER.USER_NAME.eq(userName));

        if (!StringUtil.safeIsBlank(emailId) && !User.PLACEHOLDER.equals(emailId))
            conditions.add(SECURITY_USER.EMAIL_ID.eq(emailId));

        if (!StringUtil.safeIsBlank(phoneNumber) && !User.PLACEHOLDER.equals(phoneNumber))
            conditions.add(SECURITY_USER.PHONE_NUMBER.eq(phoneNumber));

        conditions.add(SECURITY_USER.CLIENT_ID.eq(clientId));

        return Mono.from(this.dslContext.selectCount().from(SECURITY_USER).where(DSL.and(conditions))).map(e -> e.value1() > 0);
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


        List<Condition> conditions = new ArrayList<>();

        if (!StringUtil.safeIsBlank(userName) && !User.PLACEHOLDER.equals(userName)) {
            TableField<SecurityUserRecord, String> userIdentificationField;

            switch (authenticationIdentifierType) {
                case PHONE_NUMBER -> userIdentificationField = SECURITY_USER.PHONE_NUMBER;
                case EMAIL_ID -> userIdentificationField = SECURITY_USER.EMAIL_ID;
                default -> userIdentificationField = SECURITY_USER.USER_NAME;
            }
            conditions.add(userIdentificationField.eq(userName));
        }

        if (userStatusCodes != null && userStatusCodes.length > 0)
            conditions.add(SECURITY_USER.STATUS_CODE.in(userStatusCodes));

        if (userId != null)
            conditions.add(SECURITY_USER.ID.eq(userId));

        if (appCode != null)
            conditions.add(SECURITY_APP
                    .CLIENT_ID
                    .eq(SECURITY_USER.CLIENT_ID)
                    .or(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID)));

        conditions.add(ClientHierarchyDAO.getManageClientCondition(SECURITY_CLIENT.ID));

        SelectJoinStep<Record> query = this.dslContext.select(fields).from(SECURITY_USER);

        if (appCode != null) {
            query = query.leftJoin(SECURITY_APP)
                    .on(SECURITY_APP.APP_CODE.eq(appCode))
                    .leftJoin(SECURITY_APP_ACCESS)
                    .on(SECURITY_APP_ACCESS
                            .CLIENT_ID
                            .eq(SECURITY_USER.CLIENT_ID)
                            .and(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)));
        }

        return query.leftJoin(SECURITY_CLIENT)
                .on(SECURITY_CLIENT.CODE.eq(clientCode))
                .leftJoin(SECURITY_CLIENT_HIERARCHY)
                .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
                .where(DSL.and(conditions));
    }

    public Mono<Map<ULong, ULong>> getAllClientsBy(String userName, ULong userId, String clientCode, String appCode,
                                                   AuthenticationIdentifierType identifierType, SecurityUserStatusCode... userStatusCodes) {

        return Flux
                .from(this.getAllUsersPerAppQuery(userName, userId, clientCode, appCode, identifierType,
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
                        .select(DSL.val(userId).as("USER_ID"),
                                DSL.coalesce(SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID,
                                        SecurityProfile.SECURITY_PROFILE.ID).as("PROFILE_ID"))
                        .from(SecurityProfile.SECURITY_PROFILE)
                        .where(SecurityProfile.SECURITY_PROFILE.ID.eq(profileId)).limit(1))
                .flatMap(rec ->
                        Mono.from(this.dslContext
                                .insertInto(SecurityProfileUser.SECURITY_PROFILE_USER,
                                        SecurityProfileUser.SECURITY_PROFILE_USER.USER_ID,
                                        SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID)
                                .values(rec.value1(), rec.value2())
                                .onDuplicateKeyIgnore()))
                .map(value -> value > 0 ? 1 : 0);
    }

    public Mono<Boolean> addDesignation(ULong userId, ULong designationId) {
        return Mono
                .from(this.dslContext.update(SecurityUser.SECURITY_USER)
                        .set(SecurityUser.SECURITY_USER.DESIGNATION_ID, designationId)
                        .where(SecurityUser.SECURITY_USER.ID.eq(userId)))
                .map(e -> e > 0);
    }

    public Mono<Boolean> canReportTo(ULong clientId, ULong reportingTo, ULong userId) {

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.selectCount().from(SECURITY_USER).where(DSL.and(
                        SECURITY_USER.ID.eq(reportingTo),
                        SECURITY_USER.CLIENT_ID.eq(clientId)
                ))).map(r -> r.value1() == 1),

                sameClient -> {
                    if (!BooleanUtil.safeValueOf(sameClient) || reportingTo.equals(userId)) return Mono.just(false);
                    if (userId == null) return Mono.just(true);

                    return Mono.just(List.of(userId))
                            .expand(userList -> Flux.from(this.dslContext.select(SECURITY_USER.ID).from(SECURITY_USER).where(SECURITY_USER.REPORTING_TO.in(userList))).map(Record1::value1).collectList())
                            .map(userList -> userList.contains(reportingTo))
                            .reduce(Boolean::logicalAnd);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserDAO.canReportTo"));
    }

    public Flux<ULong> getUserIdsByClientId(ULong clientId, List<ULong> userIds) {

        return this.clientDAO.getClientTypeNCode(clientId)
                .flatMapMany(typeAndCode -> {
                    List<Condition> conditions = new ArrayList<>();
                    conditions.add(SECURITY_USER.CLIENT_ID.eq(clientId));
                    conditions.add(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED));

                    boolean isSystemClient = ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(typeAndCode.getT1());

                    if (!isSystemClient) {
                        conditions.add(ClientHierarchyDAO.getManageClientCondition(clientId));
                    }

                    if (userIds != null && !userIds.isEmpty())
                        conditions.add(SECURITY_USER.ID.in(userIds));

                    SelectJoinStep<Record1<ULong>> query = this.dslContext
                            .select(SECURITY_USER.ID)
                            .from(SECURITY_USER);

                    if (!isSystemClient) {
                        query = query.leftJoin(SECURITY_CLIENT_HIERARCHY)
                                .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID));
                    }

                    return Flux.from(query.where(DSL.and(conditions)))
                            .map(Record1::value1);
                })
                .switchIfEmpty(Flux.empty());
    }

    public Flux<ULong> getLevel1SubOrg(ULong clientId, ULong userId) {
        return Flux.from(this.dslContext
                        .select(SECURITY_USER.ID)
                        .from(SECURITY_USER)
                        .where(DSL.and(SECURITY_USER.REPORTING_TO.eq(userId), SECURITY_USER.CLIENT_ID.eq(clientId))))
                .map(Record1::value1);
    }

    public Mono<Boolean> checkIfUserIsOwner(ULong userId) {

        return Mono.from(this.dslContext
                        .select(SECURITY_V2_ROLE.NAME)
                        .from(SECURITY_PROFILE)
                        .leftJoin(SECURITY_PROFILE_USER)
                        .on(SECURITY_PROFILE_USER.PROFILE_ID.eq(SECURITY_PROFILE.ID))
                        .leftJoin(SECURITY_PROFILE_ROLE)
                        .on(SECURITY_PROFILE_ROLE.PROFILE_ID.eq(SECURITY_PROFILE.ID))
                        .leftJoin(SECURITY_V2_ROLE)
                        .on(SECURITY_V2_ROLE.ID.eq(SECURITY_PROFILE_ROLE.ROLE_ID))
                        .where(SECURITY_PROFILE_USER.USER_ID.eq(userId))
                        .and(SECURITY_V2_ROLE.NAME.eq("Owner"))
                        .limit(1))
                .map(Objects::nonNull)
                .defaultIfEmpty(false);
    }

    public Mono<List<User>> getUsersForProfiles(List<ULong> profileIds) {
        return Flux.from(this.dslContext
                        .select(SECURITY_USER.fields())
                        .from(SECURITY_PROFILE_USER)
                        .leftJoin(SECURITY_PROFILE)
                        .on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                        .leftJoin(SECURITY_USER)
                        .on(SECURITY_USER.ID.eq(SECURITY_PROFILE_USER.USER_ID))
                        .where(SECURITY_PROFILE_USER.PROFILE_ID.in(profileIds)))
                .map(record -> record.into(User.class))
                .distinct()
                .collectList();
    }

    public Mono<List<User>> getOwners(ULong clientId) {

        if (clientId == null) {
            return Mono.just(List.of());
        }

        return Flux.from(this.dslContext.select(SECURITY_USER.fields())
                        .from(SECURITY_USER)
                        .join(SECURITY_PROFILE_USER)
                        .on(SECURITY_PROFILE_USER.USER_ID.eq(SECURITY_USER.ID))
                        .join(SECURITY_PROFILE)
                        .on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                        .join(SECURITY_PROFILE_ROLE)
                        .on(SECURITY_PROFILE_ROLE.PROFILE_ID.eq(SECURITY_PROFILE.ID))
                        .join(SECURITY_V2_ROLE)
                        .on(SECURITY_V2_ROLE.ID.eq(SECURITY_PROFILE_ROLE.ROLE_ID))
                        .where(DSL.and(
                                SECURITY_V2_ROLE.NAME.eq("Owner"),
                                SECURITY_USER.CLIENT_ID.eq(clientId),
                                SECURITY_USER.STATUS_CODE.eq(SecurityUserStatusCode.ACTIVE)
                        )))
                .map(record -> record.into(User.class))
                .distinct()
                .collectList();
    }
}
