package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityAppPackage.SECURITY_APP_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityAppProperty.SECURITY_APP_PROPERTY;
import static com.fincity.security.jooq.tables.SecurityAppUserRole.SECURITY_APP_USER_ROLE;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityAppUserRole;
import com.fincity.security.jooq.tables.SecurityClientUrl;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecuritySslCertificate;
import com.fincity.security.jooq.tables.SecuritySslChallenge;
import com.fincity.security.jooq.tables.SecuritySslRequest;
import com.fincity.security.jooq.tables.records.SecurityAppAccessRecord;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class AppDAO extends AbstractUpdatableDAO<SecurityAppRecord, ULong, App> {

	protected AppDAO() {
		super(App.class, SECURITY_APP, SECURITY_APP.ID);
	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
				.map(ca -> {

			        SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields()))
			                .from(table);

			        SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count())
			                .from(table);

			        if (ca.getClientTypeCode()
			                .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
				        return Tuples.of(mainQuery, countQuery);

			        return Tuples.of((SelectJoinStep<Record>) mainQuery.leftJoin(SECURITY_APP_ACCESS)
			                .on(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)
			                        .and(SECURITY_APP_ACCESS.CLIENT_ID.eq(ULong.valueOf(ca.getUser()
			                                .getClientId())))),
			                (SelectJoinStep<Record1<Integer>>) countQuery.leftJoin(SECURITY_APP_ACCESS)
			                        .on(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID)
			                                .and(SECURITY_APP_ACCESS.CLIENT_ID.eq(ULong.valueOf(ca.getUser()
			                                        .getClientId())))));
		        });
	}

	@Override
	protected Mono<Condition> filter(AbstractCondition acond) {

		Mono<Condition> condition = super.filter(acond);

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
			        if (ca.isSystemClient())
				        return condition;

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());

			        return condition.map(c -> DSL.and(c, SECURITY_APP.CLIENT_ID.eq(clientId)
			                .or(SECURITY_APP_ACCESS.CLIENT_ID.eq(clientId)
			                        .and(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf((byte) 1))))));
		        })
		        .switchIfEmpty(condition);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {

		return hasOnlyInternalAccess(appCode, clientCode, 1);
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
		return hasOnlyInternalAccess(appCode, clientCode, 0);
	}

	public Mono<Boolean> hasReadAccess(ULong appId, ULong clientId) {

		return hasOnlyInternalAccess(appId, clientId, 0);
	}

	public Mono<Boolean> hasWriteAccess(ULong appId, ULong clientId) {

		return hasOnlyInternalAccess(appId, clientId, 1);
	}

	private Mono<Boolean> hasOnlyInternalAccess(ULong appId, ULong clientId, int accessType) {

		List<Condition> conditions = new ArrayList<>();
		conditions.add(SECURITY_APP_ACCESS.APP_ID.eq(appId));
		conditions.add(SECURITY_CLIENT.ID.eq(clientId));
		if (accessType == 1) {
			conditions.add(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1)));
		}

		SelectConditionStep<Record1<ULong>> inQuery = this.dslContext.select(SECURITY_APP_ACCESS.APP_ID)
		        .from(SECURITY_APP_ACCESS)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP_ACCESS.CLIENT_ID))
		        .where(DSL.and(conditions));

		var outQuery = this.dslContext.select(DSL.count())
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
		        .where(SECURITY_APP.ID.eq(appId)
		                .and(SECURITY_CLIENT.ID.eq(clientId)
		                        .or(SECURITY_APP.ID.in(inQuery))))
		        .limit(1);

		return Mono.from(outQuery)
		        .map(Record1::value1)
		        .map(e -> e != 0);
	}

	private Mono<Boolean> hasOnlyInternalAccess(String appCode, String clientCode, int accessType) {

		List<Condition> conditions = new ArrayList<>();
		conditions.add(SECURITY_CLIENT.CODE.eq(clientCode));
		if (accessType == 1) {
			conditions.add(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1)));
		}

		SelectConditionStep<Record1<ULong>> inQuery = this.dslContext.select(SECURITY_APP_ACCESS.APP_ID)
		        .from(SECURITY_APP_ACCESS)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP_ACCESS.CLIENT_ID))
		        .where(DSL.and(conditions));

		return Mono.from(this.dslContext.select(DSL.count())
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
		        .where(SECURITY_APP.APP_CODE.eq(appCode)
		                .and(SECURITY_CLIENT.CODE.eq(clientCode)
		                        .or(SECURITY_APP.ID.in(inQuery))))
		        .limit(1))
		        .map(Record1::value1)
		        .map(e -> e != 0);
	}

	public Mono<Boolean> hasAppEditAccess(ULong appId, ULong clientId) {

		return Mono.from(

		        this.dslContext.select(SECURITY_APP_ACCESS.EDIT_ACCESS)
		                .from(SECURITY_APP_ACCESS)
		                .where(SECURITY_APP_ACCESS.APP_ID.eq(appId)
		                        .and(SECURITY_APP_ACCESS.CLIENT_ID.eq(clientId))))
		        .map(Record1::value1)
		        .map(e -> e.equals(UByte.valueOf(1)));

	}

	public Mono<Boolean> hasPackageAssignedWithApp(ULong appId, ULong clientId, ULong packageId) {

		return Mono.from(this.dslContext.select(DSL.count())
		        .from(SECURITY_APP_PACKAGE)
		        .where(SECURITY_APP_PACKAGE.APP_ID.eq(appId)
		                .and(SECURITY_APP_PACKAGE.CLIENT_ID.eq(clientId))
		                .and(SECURITY_APP_PACKAGE.PACKAGE_ID.eq(packageId))))
		        .map(Record1::value1)
		        .map(e -> e > 0);
	}

	public Mono<Boolean> hasRoleAssignedWithApp(ULong appId, ULong clientId, ULong roleId) {

		return Mono.from(

		        this.dslContext.selectCount()
		                .from(SECURITY_APP_USER_ROLE)
		                .where(SECURITY_APP_USER_ROLE.APP_ID.eq(appId)
		                        .and(SECURITY_APP_USER_ROLE.CLIENT_ID.eq(clientId))
		                        .and(SECURITY_APP_USER_ROLE.ROLE_ID.eq(roleId))))
		        .map(Record1::value1)
		        .map(e -> e > 0);
	}

	public Mono<Boolean> addRoleAccess(ULong appId, ULong clientId, ULong roleId) {

		return Mono.from(

		        this.dslContext.insertInto(SECURITY_APP_USER_ROLE)
		                .columns(SECURITY_APP_USER_ROLE.APP_ID, SECURITY_APP_USER_ROLE.CLIENT_ID,
		                        SECURITY_APP_USER_ROLE.ROLE_ID)
		                .values(appId, clientId, roleId)

		)
		        .map(e -> e == 1);
	}

	public Flux<ULong> fetchRolesBasedOnClient(ULong clientId, ULong appId) {

		return Flux.from(

		        this.dslContext.select(SECURITY_APP_USER_ROLE.ROLE_ID)
		                .from(SECURITY_APP_USER_ROLE)
		                .where(SECURITY_APP_USER_ROLE.CLIENT_ID.eq(clientId)
		                        .and(SECURITY_APP_USER_ROLE.APP_ID.eq(appId)))

		)
		        .map(Record1::value1)
		        .map(ULongUtil::valueOf);
	}

	public Flux<ULong> fetchPackagesBasedOnClient(ULong clientId, ULong appId) {

		return Flux.from(

		        this.dslContext.select(SECURITY_APP_PACKAGE.PACKAGE_ID)
		                .from(SECURITY_APP_PACKAGE)
		                .where(SECURITY_APP_PACKAGE.CLIENT_ID.eq(clientId)
		                        .and(SECURITY_APP_PACKAGE.APP_ID.eq(appId))))
		        .map(Record1::value1);

	}

	public Mono<Boolean> addPackageAccess(ULong appId, ULong clientId, ULong packageId) {

		return Mono.from(

		        this.dslContext.insertInto(SECURITY_APP_PACKAGE)
		                .columns(SECURITY_APP_PACKAGE.CLIENT_ID, SECURITY_APP_PACKAGE.APP_ID,
		                        SECURITY_APP_PACKAGE.PACKAGE_ID)
		                .values(clientId, appId, packageId)

		)
		        .map(e -> e == 1);

	}

	public Mono<Boolean> removePackageAccess(ULong id, ULong clientId, ULong packageId) {

		return Mono.from(

		        this.dslContext.deleteFrom(SECURITY_APP_PACKAGE)
		                .where(SECURITY_APP_PACKAGE.APP_ID.eq(id)
		                        .and(SECURITY_APP_PACKAGE.CLIENT_ID.eq(clientId))
		                        .and(SECURITY_APP_PACKAGE.PACKAGE_ID.eq(packageId))))
		        .map(e -> e == 1);

	}

	public Mono<Boolean> removeRoleAccess(ULong appId, ULong clientId, ULong roleId) {

		return Mono.from(

		        this.dslContext.deleteFrom(SECURITY_APP_USER_ROLE)
		                .where(SECURITY_APP_USER_ROLE.APP_ID.eq(appId)
		                        .and(SECURITY_APP_USER_ROLE.CLIENT_ID.eq(clientId))
		                        .and(SECURITY_APP_USER_ROLE.ROLE_ID.eq(roleId)))

		)
		        .map(e -> e == 1);
	}

	public Mono<Boolean> addClientAccess(ULong appId, ULong clientId, boolean writeAccess) {

		UByte edit = UByte.valueOf(writeAccess ? 1 : 0);

		return SecurityContextUtil.getUsersContextUser()
		        .map(ContextUser::getId)
		        .map(ULong::valueOf)
		        .flatMap(userId -> Mono.from(this.dslContext.insertInto(SECURITY_APP_ACCESS)
		                .columns(SECURITY_APP_ACCESS.APP_ID, SECURITY_APP_ACCESS.CLIENT_ID,
		                        SECURITY_APP_ACCESS.EDIT_ACCESS, SECURITY_APP_ACCESS.CREATED_BY)
		                .values(appId, clientId, edit, userId)
		                .onDuplicateKeyUpdate()
		                .set(SECURITY_APP_ACCESS.EDIT_ACCESS, edit)
		                .set(SECURITY_APP_ACCESS.UPDATED_BY, userId)))
		        .map(e -> e == 1);
	}

	public Mono<Boolean> removeClientAccess(ULong appId, ULong accessId) {

		return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_ACCESS)
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)
		                .and(SECURITY_APP_ACCESS.APP_ID.eq(appId))))
		        .map(e -> e == 1);
	}

	public Mono<Boolean> updateClientAccess(ULong accessId, boolean writeAccess) {

		return Mono.from(this.dslContext.update(SECURITY_APP_ACCESS)
		        .set(SECURITY_APP_ACCESS.EDIT_ACCESS, UByte.valueOf(writeAccess ? 1 : 0))
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)))
		        .map(e -> e == 1);
	}

	public Mono<SecurityAppAccessRecord> readClientAccess(ULong accessId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACCESS)
		        .where(SECURITY_APP_ACCESS.ID.eq(accessId)));
	}

	public Mono<List<String>> appInheritance(String appCode, String urlClientCode, String clientCode) {

		return Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
		        .from(SECURITY_APP)
		        .leftJoin(SECURITY_CLIENT)
		        .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .limit(1))
		        .map(Record1::value1)
		        .map(code ->
				{

			        if (urlClientCode == null) {
				        return clientCode.equals(code) ? List.of(code) : List.of(code, clientCode);
			        }

			        if (urlClientCode.equals(clientCode)) {
				        return clientCode.equals(code) ? List.of(code) : List.of(code, clientCode);
			        }

			        List<String> clientList = new ArrayList<>();

			        clientList.add(code);
			        clientList.add(urlClientCode);
			        clientList.add(clientCode);

			        return clientList;
		        });
	}

	public Flux<ULong> getClientIdsWithAccess(String appCode, boolean onlyWriteAccess) {

		Condition accessCheckCondition = SECURITY_APP.APP_CODE.eq(appCode);
		if (onlyWriteAccess)
			accessCheckCondition = accessCheckCondition.and(SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1)));

		return Flux.from(this.dslContext.select(SECURITY_APP.CLIENT_ID)
		        .from(SECURITY_APP)
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .union(this.dslContext.select(SECURITY_APP_ACCESS.CLIENT_ID)
		                .from(SECURITY_APP_ACCESS)
		                .leftJoin(SECURITY_APP)
		                .on(SECURITY_APP.ID.eq(SECURITY_APP_ACCESS.APP_ID))
		                .where(accessCheckCondition)))
		        .map(Record1::value1)
		        .distinct()
		        .sort();
	}

	public Mono<App> getByAppCode(String appCode) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_APP)
		        .where(SECURITY_APP.APP_CODE.eq(appCode))
		        .limit(1))
		        .map(e -> e.into(App.class));
	}

	public Mono<String> generateAppCode(App app) {

		String appName = StringUtil.safeValueOf(app.getAppName(), "newapp");

		if (appName.length() > 24)
			appName = appName.substring(0, 24);

		return Mono.just(UniqueUtil.uniqueNameOnlyLetters(36, appName))
		        .expandDeep(n -> Mono.from(this.dslContext.selectCount()
		                .from(SECURITY_APP)
		                .where(SECURITY_APP.APP_CODE.eq(n)))
		                .map(Record1::value1)
		                .flatMap(count ->
						{
			                if (count == 0l)
				                return Mono.empty();

			                return Mono.just(UniqueUtil.uniqueNameOnlyLetters(36, n));
		                }))
		        .collectList()
		        .map(lst -> lst.get(lst.size() - 1));
	}

	public Mono<Map<ULong, Map<String, AppProperty>>> getProperties(List<ULong> clientIds, ULong appId, String appCode,
	        String propName) {

		return FlatMapUtil.flatMapMono(

		        () -> Mono.from(this.dslContext.selectFrom(SECURITY_APP)
		                .where(appId != null ? SECURITY_APP.ID.eq(appId) : SECURITY_APP.APP_CODE.eq(appCode))
		                .limit(1))
		                .map(e -> e.into(App.class)),

		        app ->
				{

			        List<Condition> conditions = new ArrayList<>();
			        conditions.add(SECURITY_APP_PROPERTY.APP_ID.eq(app.getId()));
			        if (propName != null)
				        conditions.add(SECURITY_APP_PROPERTY.NAME.eq(propName));

			        if (clientIds != null && !clientIds.isEmpty())
				        conditions.add(SECURITY_APP_PROPERTY.CLIENT_ID.eq(app.getClientId())
				                .or(SECURITY_APP_PROPERTY.CLIENT_ID.in(clientIds)));
			        else
				        conditions.add(SECURITY_APP_PROPERTY.CLIENT_ID.eq(app.getClientId()));

			        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_PROPERTY)
			                .where(DSL.and(conditions)))
			                .map(e -> e.into(AppProperty.class))
			                .collectList()
			                .<Map<ULong, Map<String, AppProperty>>>map(
			                        lst -> this.convertAttributesToMap(app.getClientId(), clientIds, lst));
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.getProperties"));
	}

	public Map<ULong, Map<String, AppProperty>> convertAttributesToMap(ULong appClientId, List<ULong> clientIds,
	        List<AppProperty> lst) {

		Map<String, AppProperty> appDefaultProps = new HashMap<>();
		Map<ULong, Map<String, AppProperty>> appClientProps = new HashMap<>();

		for (AppProperty prop : lst) {
			if (prop.getClientId()
			        .equals(appClientId)) {
				appDefaultProps.put(prop.getName(), prop);
			}
			if (!appClientProps.containsKey(prop.getClientId()))
				appClientProps.put(prop.getClientId(), new HashMap<>());
			appClientProps.get(prop.getClientId())
			        .put(prop.getName(), prop);
		}

		for (Entry<ULong, Map<String, AppProperty>> entry : appClientProps.entrySet()) {

			if (entry.getKey()
			        .equals(appClientId))
				continue;

			for (Entry<String, AppProperty> defaultProp : appDefaultProps.entrySet()) {
				if (!entry.getValue()
				        .containsKey(defaultProp.getKey()))
					entry.getValue()
					        .put(defaultProp.getKey(), defaultProp.getValue());
			}
		}

		if (clientIds != null && !clientIds.contains(appClientId)) {
			appClientProps.remove(appClientId);
		}

		return appClientProps;
	}

	public Mono<Boolean> updateProperty(AppProperty property) {
		return Mono.from(this.dslContext.insertInto(SECURITY_APP_PROPERTY)
		        .columns(SECURITY_APP_PROPERTY.APP_ID, SECURITY_APP_PROPERTY.CLIENT_ID, SECURITY_APP_PROPERTY.NAME,
		                SECURITY_APP_PROPERTY.VALUE)
		        .values(property.getAppId(), property.getClientId(), property.getName(), property.getValue())
		        .onDuplicateKeyUpdate()
		        .set(SECURITY_APP_PROPERTY.VALUE, property.getValue()))
		        .map(e -> true);
	}

	public Mono<Boolean> deleteProperty(ULong clientId, ULong appId, String propName) {
		return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_PROPERTY)
		        .where(SECURITY_APP_PROPERTY.APP_ID.eq(appId)
		                .and(SECURITY_APP_PROPERTY.CLIENT_ID.eq(clientId))
		                .and(SECURITY_APP_PROPERTY.NAME.eq(propName))))
		        .map(e -> e == 1);
	}

	public Mono<Boolean> isNoneUsingTheAppOtherThan(ULong appId, BigInteger bigInteger) {
		return Mono.just(this.dslContext.selectCount()
		        .from(SECURITY_APP_ACCESS)
		        .where(SECURITY_APP_ACCESS.APP_ID.eq(appId)
		                .and(SECURITY_APP_ACCESS.CLIENT_ID.ne(ULongUtil.valueOf(bigInteger))))
		        .fetchOne())
		        .map(Record1::value1)
		        .map(e -> e == 0);
	}

	public Mono<Boolean> deleteEverythingRelated(ULong appId, String appCode) {

		return Mono.from(this.dslContext.transactionPublisher(config -> {

			var dsl = config.dsl();

			Mono<List<ULong>> urlIds = Flux.from(dsl.select(SecurityClientUrl.SECURITY_CLIENT_URL.ID)
			        .from(SecurityClientUrl.SECURITY_CLIENT_URL)
			        .where(SecurityClientUrl.SECURITY_CLIENT_URL.APP_CODE.eq(appCode)))
			        .map(Record1::value1)
			        .collectList();

			Mono<List<ULong>> permissionIds = Flux.from(dsl.select(SecurityPermission.SECURITY_PERMISSION.ID)
			        .from(SecurityPermission.SECURITY_PERMISSION)
			        .where(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(appId)))
			        .map(Record1::value1)
			        .collectList();

			Mono<List<ULong>> roleIds = Flux.from(dsl.select(SecurityPermission.SECURITY_PERMISSION.ID)
			        .from(SecurityPermission.SECURITY_PERMISSION)
			        .where(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(appId)))
			        .map(Record1::value1)
			        .collectList();

			return FlatMapUtil.flatMapMono(

			        () -> Mono.zip(

			                Mono.from(dsl.deleteFrom(SECURITY_APP_ACCESS)
			                        .where(SECURITY_APP_ACCESS.APP_ID.eq(appId))),

			                Mono.from(dsl.deleteFrom(SECURITY_APP_USER_ROLE)
			                        .where(SECURITY_APP_USER_ROLE.APP_ID.eq(appId))),

			                Mono.from(dsl.deleteFrom(SECURITY_APP_PACKAGE)
			                        .where(SECURITY_APP_PACKAGE.APP_ID.eq(appId))),

			                Mono.from(dsl.deleteFrom(SECURITY_APP_PROPERTY)
			                        .where(SECURITY_APP_PROPERTY.APP_ID.eq(appId)))

					),

			        a -> Mono.zip(urlIds, permissionIds, roleIds),

			        (a, tuple) -> Flux.from(dsl.select(SecuritySslRequest.SECURITY_SSL_REQUEST.ID)
			                .from(SecuritySslRequest.SECURITY_SSL_REQUEST)
			                .where(SecuritySslRequest.SECURITY_SSL_REQUEST.URL_ID.in(tuple.getT1())))
			                .map(Record1::value1)
			                .collectList(),

			        (a, tuple, requests) -> Mono.zip(

			                Mono.from(dsl.delete(SecuritySslCertificate.SECURITY_SSL_CERTIFICATE)
			                        .where(SecuritySslCertificate.SECURITY_SSL_CERTIFICATE.URL_ID.in(tuple.getT1()))),

			                FlatMapUtil.flatMapMono(
			                        () -> Mono.from(dsl.delete(SecuritySslChallenge.SECURITY_SSL_CHALLENGE)
			                                .where(SecuritySslChallenge.SECURITY_SSL_CHALLENGE.REQUEST_ID
			                                        .in(requests))),

			                        x -> Mono.from(dsl.delete(SecuritySslRequest.SECURITY_SSL_REQUEST)
			                                .where(SecuritySslRequest.SECURITY_SSL_REQUEST.ID.in(requests))),

			                        (x, y) -> Mono.from(dsl.delete(SecurityClientUrl.SECURITY_CLIENT_URL)
			                                .where(SecurityClientUrl.SECURITY_CLIENT_URL.ID.in(tuple.getT1())))),

			                Mono.from(dsl.delete(SecurityPermission.SECURITY_PERMISSION)
			                        .where(SecurityPermission.SECURITY_PERMISSION.ID.in(tuple.getT2()))),

			                Mono.from(dsl.delete(SecurityAppUserRole.SECURITY_APP_USER_ROLE)
			                        .where(SecurityAppUserRole.SECURITY_APP_USER_ROLE.ROLE_ID.in(tuple.getT3())))

					),

			        (a, tuple, requests, x) -> Mono.from(dsl.delete(SecurityApp.SECURITY_APP)
			                .where(SecurityApp.SECURITY_APP.ID.eq(appId)))
			                .map(e -> e == 1)

			);
		}))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.deleteEverythingRelated"));
	}

	public Mono<Boolean> createPropertyFromTransport(ULong appId, ULong clientId, String propertyName,
	        String propertyValue) {

		return Mono.from(this.dslContext.insertInto(SECURITY_APP_PROPERTY)
		        .columns(SECURITY_APP_PROPERTY.APP_ID, SECURITY_APP_PROPERTY.CLIENT_ID, SECURITY_APP_PROPERTY.NAME,
		                SECURITY_APP_PROPERTY.VALUE)
		        .values(appId, clientId, propertyName, propertyValue)
		        .onDuplicateKeyUpdate()
		        .set(SECURITY_APP_PROPERTY.VALUE, propertyValue))
		        .map(e -> e == 1);
	}

	public Mono<Page<App>> readAnyAppsPageFilter(org.springframework.data.domain.Pageable pageable,
	        AbstractCondition condition, ULong clientId) {

		return FlatMapUtil.flatMapMono(

		        () -> super.filter(condition),

		        filterCondition ->
				{

			        List<SortField<?>> orderBy = new ArrayList<>();

			        pageable.getSort()
			                .forEach(order ->
							{
				                Field<?> field = this.getField(order.getProperty());
				                if (field != null)
					                orderBy.add(field.sort(
					                        order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
			                });

			        Condition anyAppCondition = DSL.and(filterCondition, SECURITY_APP.CLIENT_ID.eq(clientId),
			                SECURITY_APP.APP_ACCESS_TYPE.eq(SecurityAppAppAccessType.ANY));

			        final Mono<Integer> recordsCount = Mono.from(this.dslContext.selectCount()
			                .from(SECURITY_APP)
			                .where(anyAppCondition))
			                .map(Record1::value1);

			        SelectConditionStep<SecurityAppRecord> selectJoinStep = this.dslContext.selectFrom(SECURITY_APP)
			                .where(anyAppCondition);
			        if (!orderBy.isEmpty()) {
				        selectJoinStep.orderBy(orderBy);
			        }

			        Mono<List<App>> recordsList = Flux.from(selectJoinStep.limit(pageable.getPageSize())
			                .offset(pageable.getOffset()))
			                .map(e -> e.into(this.pojoClass))
			                .collectList();

			        return recordsList.flatMap(list -> recordsCount
			                .map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
		        });
	}

	public Mono<Long> getAppsCountByAppIdAndClientId(ULong appId, ULong clientId) {

		Condition appCond = DSL.and(SECURITY_APP_ACCESS.APP_ID.eq(appId));

		return FlatMapUtil.flatMapMono(

		        () -> Flux.from(this.dslContext.select(SECURITY_CLIENT.ID)
		                .from(SECURITY_CLIENT)
		                .leftJoin(SECURITY_CLIENT_MANAGE)
		                .on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID))
		                .where(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(clientId)))
		                .map(Record1::value1)
		                .collectList(),

		        managedClientList -> Mono.from(this.dslContext.selectCount()
		                .from(SECURITY_APP_ACCESS)
		                .where(appCond.and(SECURITY_APP_ACCESS.CLIENT_ID.in(managedClientList))))
		                .map(Record1::value1)
		                .map(Number::longValue)

		);

	}

}
