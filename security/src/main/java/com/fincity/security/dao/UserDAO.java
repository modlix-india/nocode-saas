package com.fincity.security.dao;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityAppUserRole.SECURITY_APP_USER_ROLE;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Component
public class UserDAO extends AbstractClientCheckDAO<SecurityUserRecord, ULong, User> {

	private static final String SYSTEM = "SYSTEM";
	private static final String CLIENT_ID = "CLIENT_ID";
	private static final String APP_CODE = "APP_CODE";
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
		        .select(SECURITY_CLIENT.CODE.as("clientCode"))
		        .from(SECURITY_USER)
		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_USER.CLIENT_ID))
		        .where(field.eq(userName)
		                .and(SECURITY_USER.CLIENT_ID.eq(clientId)
		                        .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(clientId)))
		                .and(SECURITY_USER.STATUS_CODE.eq(SecurityUserStatusCode.ACTIVE))
		                .and(SECURITY_CLIENT.STATUS_CODE.eq(SecurityClientStatusCode.ACTIVE)))
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
		                + (SYSTEM.equals(r.value1()) ? r.value2() : r.value1() + "_" + r.value2()))
		        .map(r -> "Authorities." + r.replace(' ', '_'))
		        .collectList()
		        .map(e ->
				{
			        List<String> auths = new ArrayList<>(e);
			        auths.add("Authorities.Logged_IN");
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

	public Mono<Boolean> checkRoleCreatedByUser(ULong userId, ULong roleId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_USER_ROLE_PERMISSION)
		        .where(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.eq(roleId)
		                .and(SECURITY_USER_ROLE_PERMISSION.USER_ID.eq(userId))))
		        .map(Objects::nonNull);
	}

	public Mono<Boolean> isBeingManagedBy(ULong loggedInClientId, ULong userId) {

		return flatMapMono(

		        () -> this.readById(userId),

		        givenUser ->
				{
			        if (loggedInClientId.equals(givenUser.getClientId()))
				        return Mono.just(true);

			        return Mono.just(this.dslContext.selectFrom(SECURITY_CLIENT_MANAGE)
			                .where(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(loggedInClientId)
			                        .and(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(givenUser.getClientId())))
			                .execute() > 0);
		        }

		);
	}

	public Mono<List<PastPassword>> getPastPasswordsBasedOnPolicy(ULong userId, ULong clientId) {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT_PASSWORD_POLICY.PASS_HISTORY_COUNT)
		        .from(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId))
		        .limit(1))
		        .flatMapMany(cnt -> Flux.from(this.dslContext.select(SECURITY_PAST_PASSWORDS.fields())
		                .from(SECURITY_PAST_PASSWORDS)
		                .where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId))
		                .orderBy(SECURITY_PAST_PASSWORDS.CREATED_AT.desc())
		                .limit(cnt.value1())))
		        .map(e -> e.into(PastPassword.class))
		        .collectList()
		        .defaultIfEmpty(List.of());
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
		        .map(Objects::nonNull);
	}

	public Mono<List<User>> getBy(String userName, ULong userId, String appCode,
	        AuthenticationIdentifierType authenticationIdentifierType, boolean onlyActiveUsers) {

		var query = getAllUsersPerAppQuery(userName, userId, appCode, authenticationIdentifierType, onlyActiveUsers,
		        SECURITY_USER.fields());

		var limitQuery = query.limit(2);

		return Flux.from(limitQuery)
		        .map(e -> e.into(User.class))
		        .collectList();
	}

	private SelectConditionStep<Record> getAllUsersPerAppQuery(String userName, ULong userId, String appCode,
	        AuthenticationIdentifierType authenticationIdentifierType, boolean onlyActiveUsers, Field<?>... fields) {

		TableField<SecurityUserRecord, String> field = SECURITY_USER.USER_NAME;
		if (authenticationIdentifierType == AuthenticationIdentifierType.EMAIL_ID) {
			field = SECURITY_USER.EMAIL_ID;
		} else if (authenticationIdentifierType == AuthenticationIdentifierType.PHONE_NUMBER) {
			field = SECURITY_USER.PHONE_NUMBER;
		}

		var appA = SECURITY_APP.as("a");
		var appB = SECURITY_APP.as("b");

		List<Condition> conditions = new ArrayList<>();

		conditions.add(field.eq(userName));
		if (onlyActiveUsers)
			conditions.add(SECURITY_USER.STATUS_CODE.eq(SecurityUserStatusCode.ACTIVE));
		else
			conditions.add(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED));
		conditions.add(DSL.or(

		        appA.field(APP_CODE, String.class)
		                .eq(appCode),
		        appB.field(APP_CODE, String.class)
		                .eq(appCode)));

		if (userId != null)
			conditions.add(SECURITY_USER.ID.eq(userId));

		var accAA = SECURITY_APP_ACCESS.as("aa");

		SelectOnConditionStep<Record> query = this.dslContext.select(fields)
		        .from(SECURITY_USER)

		        .leftJoin(accAA)
		        .on(accAA.field(CLIENT_ID, ULong.class)
		                .eq(SECURITY_USER.CLIENT_ID))

		        .leftJoin(appA)
		        .on(appA.field(CLIENT_ID, ULong.class)
		                .eq(SECURITY_USER.CLIENT_ID))

		        .leftJoin(appB)
		        .on(appB.field("ID", ULong.class)
		                .eq(accAA.field("APP_ID", ULong.class)));

		return query.where(conditions);
	}

	public Mono<Map<ULong, ULong>> getAllClientsBy(String userName, String appCode,
	        AuthenticationIdentifierType identifierType) {

		return Flux
		        .from(this.getAllUsersPerAppQuery(userName, null, appCode, identifierType, true, SECURITY_USER.ID,
		                SECURITY_USER.CLIENT_ID))

		        .collectMap(e -> e.getValue(SECURITY_USER.ID), e -> e.getValue(SECURITY_USER.CLIENT_ID));
	}

	public Mono<Boolean> addDefaultRoles(ULong userId, String clientCode, String appCode) {

		return FlatMapUtil.flatMapMono(

		        () -> Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
		                .from(SECURITY_CLIENT)
		                .where(SECURITY_CLIENT.CODE.eq(clientCode))
		                .limit(1))
		                .map(Record1::value1),

		        urlClientId -> Mono.from(this.dslContext.select(SECURITY_APP.ID, SECURITY_APP.CLIENT_ID)
		                .from(SECURITY_APP)
		                .where(SECURITY_APP.APP_CODE.eq(appCode))
		                .limit(1))
		                .map(e -> Tuples.of(e.value1(), e.value2())),

		        (urlClientId,
		                appIdClientId) -> Mono.from(this.dslContext
		                        .insertInto(SECURITY_USER_ROLE_PERMISSION, SECURITY_USER_ROLE_PERMISSION.USER_ID,
		                                SECURITY_USER_ROLE_PERMISSION.ROLE_ID)
		                        .select(this.dslContext.select(DSL.value(userId)
		                                .as("USER_ID"), SECURITY_APP_USER_ROLE.ROLE_ID)
		                                .from(SECURITY_APP_USER_ROLE)
		                                .where(SECURITY_APP_USER_ROLE.APP_ID.eq(appIdClientId.getT1())
		                                        .and(SECURITY_APP_USER_ROLE.CLIENT_ID.eq(urlClientId))))),

		        (urlClientId, appIdClientId, insertCount) ->
				{

			        if (insertCount > 0)
				        return Mono.just(true);

			        var selectQuery = this.dslContext.select(DSL.value(userId)
			                .as("USER_ID"), SECURITY_APP_USER_ROLE.ROLE_ID)
			                .from(SECURITY_APP_USER_ROLE)
			                .where(SECURITY_APP_USER_ROLE.APP_ID.eq(appIdClientId.getT1())
			                        .and(SECURITY_APP_USER_ROLE.CLIENT_ID.eq(appIdClientId.getT2())));

			        return Mono
			                .from(this.dslContext
			                        .insertInto(SECURITY_USER_ROLE_PERMISSION, SECURITY_USER_ROLE_PERMISSION.USER_ID,
			                                SECURITY_USER_ROLE_PERMISSION.ROLE_ID)
			                        .select(selectQuery))
			                .map(count -> count > 0);

		        }

		);
	}

	public Mono<Boolean> makeUserActiveIfInActive(BigInteger id) {

		ULong uid = ULong.valueOf(id);

		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.ACTIVE)
		        .where(SECURITY_USER.ID.eq(uid)
		                .and(SECURITY_USER.STATUS_CODE.eq(SecurityUserStatusCode.INACTIVE))))
		        .map(e -> e > 0);
	}

	public Mono<Boolean> checkUserExists(String urlAppCode, String urlClientCode, ClientRegistrationRequest request) {

		List<Condition> userFieldsConditions = new ArrayList<>();

		if (!StringUtil.safeIsBlank(request.getUserName()))
			userFieldsConditions.add(SECURITY_USER.USER_NAME.eq(request.getUserName()));

		if (!StringUtil.safeIsBlank(request.getEmailId()))
			userFieldsConditions.add(SECURITY_USER.EMAIL_ID.eq(request.getEmailId()));

		if (!StringUtil.safeIsBlank(request.getPhoneNumber()))
			userFieldsConditions.add(SECURITY_USER.PHONE_NUMBER.eq(request.getPhoneNumber()));

		var c1 = SECURITY_CLIENT.as("c1");
		var c2 = SECURITY_CLIENT.as("c2");

		var query = this.dslContext.selectCount()
		        .from(SECURITY_USER)

		        .leftJoin(c1)
		        .on(c1.field("ID", ULong.class)
		                .eq(SECURITY_USER.CLIENT_ID))

		        .leftJoin(SECURITY_APP_ACCESS)
		        .on(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))

		        .leftJoin(SECURITY_APP)
		        .on(SECURITY_APP.ID.eq(SECURITY_APP_ACCESS.APP_ID))

		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))

		        .leftJoin(c2)
		        .on(c2.field("ID", ULong.class)
		                .eq(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID))

		        .where(DSL.and(

		                SECURITY_APP.APP_CODE.eq(urlAppCode),

		                c1.field("TYPE_CODE", String.class)
		                        .eq("INDV"),

		                c2.field("CODE", String.class)
		                        .eq(urlClientCode),

		                DSL.or(userFieldsConditions)));

		return Mono.from(query)
		        .map(Record1::value1)
		        .map(e -> e > 0);
	}

	public Mono<Boolean> updateUserStatus(ULong reqUserId) {

		return Mono.from(this.dslContext.update(SECURITY_USER)
		        .set(SECURITY_USER.STATUS_CODE, SecurityUserStatusCode.ACTIVE)
		        .set(SECURITY_USER.ACCOUNT_NON_EXPIRED, ByteUtil.ONE)
		        .set(SECURITY_USER.ACCOUNT_NON_LOCKED, ByteUtil.ONE)
		        .set(SECURITY_USER.CREDENTIALS_NON_EXPIRED, ByteUtil.ONE)
		        .set(SECURITY_USER.NO_FAILED_ATTEMPT, Short.valueOf((short) 0))
		        .where(SECURITY_USER.ID.eq(reqUserId)
		                .and(SECURITY_USER.STATUS_CODE.ne(SecurityUserStatusCode.DELETED))))
		        .map(e -> e > 0);
	}

}