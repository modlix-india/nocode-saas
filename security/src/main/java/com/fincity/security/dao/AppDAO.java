package com.fincity.security.dao;

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
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.tables.*;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.model.AppDependency;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityAppDependency.SECURITY_APP_DEPENDENCY;
import static com.fincity.security.jooq.tables.SecurityAppProperty.SECURITY_APP_PROPERTY;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;

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
            .flatMap(ca -> {
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

    public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
        return hasOnlyInternalAccess(appCode, clientCode, 0);
    }

    public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {
        return hasOnlyInternalAccess(appCode, clientCode, 1);
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

    public Mono<List<String>> appInheritance(String appCode, String urlClientCode, String clientCode) {

        return Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
                .from(SECURITY_APP)
                .leftJoin(SECURITY_CLIENT)
                .on(SECURITY_CLIENT.ID.eq(SECURITY_APP.CLIENT_ID))
                .where(SECURITY_APP.APP_CODE.eq(appCode))
                .limit(1))
            .map(Record1::value1)
            .map(code -> {

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

    public Mono<App> getByAppCode(String appCode) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP)
                .where(SECURITY_APP.APP_CODE.eq(appCode))
                .limit(1))
            .map(e -> e.into(App.class));
    }

    public Mono<com.fincity.saas.commons.security.dto.App> getByAppCodeExplicitInfo(String appCode) {

        return FlatMapUtil.flatMapMono(
            () -> Mono.from(this.dslContext.selectFrom(SECURITY_APP)
                    .where(SECURITY_APP.APP_CODE.eq(appCode))
                    .limit(1))
                .map(e -> e.into(com.fincity.saas.commons.security.dto.App.class)),

            app -> {

                if (!"EXPLICIT".equals(app.getAppAccessType()))
                    return Mono.just(app);

                return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACCESS)
                        .where(DSL.and(SECURITY_APP_ACCESS.APP_ID.eq(ULong.valueOf(app.getId())),
                            SECURITY_APP_ACCESS.EDIT_ACCESS.eq(UByte.valueOf(1))))
                        .limit(1)).map(e -> e.getClientId().toBigInteger()).map(app::setExplicitClientId)
                    .defaultIfEmpty(app);
            });
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
                .flatMap(count -> {
                    if (count == 0L)
                        return Mono.empty();

                    return Mono.just(UniqueUtil.uniqueNameOnlyLetters(36, n));
                }))
            .collectList()
            .map(List::getLast);
    }

    public Mono<Map<ULong, Map<String, AppProperty>>> getProperties(List<ULong> clientIds, ULong appId, String appCode,
                                                                    String propName) {

        return FlatMapUtil.flatMapMono(

            () -> Mono.from(this.dslContext.selectFrom(SECURITY_APP)
                    .where(appId != null ? SECURITY_APP.ID.eq(appId) : SECURITY_APP.APP_CODE.eq(appCode))
                    .limit(1))
                .map(e -> e.into(App.class)),

            app -> SecurityContextUtil.getUsersContextAuthentication(),

            (app, ca) -> {

                List<Condition> conditions = new ArrayList<>();
                conditions.add(SECURITY_APP_PROPERTY.APP_ID.eq(app.getId()));
                if (propName != null)
                    conditions.add(SECURITY_APP_PROPERTY.NAME.eq(propName));

                if (clientIds != null && !clientIds.isEmpty())
                    conditions.add(SECURITY_APP_PROPERTY.CLIENT_ID.eq(app.getClientId())
                        .or(SECURITY_APP_PROPERTY.CLIENT_ID.in(clientIds)));
                else if (!ca.isSystemClient())
                    conditions.add(SECURITY_APP_PROPERTY.CLIENT_ID.eq(app.getClientId()));

                var query = this.dslContext.selectFrom(SECURITY_APP_PROPERTY)
                    .where(DSL.and(conditions));

                return Flux.from(query)
                    .map(e -> e.into(AppProperty.class)).collectList()
                    .<Map<ULong, Map<String, AppProperty>>>map(
                        lst -> this.convertAttributesToMap(app.getClientId(), lst));
            }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.getProperties"));
    }

    public Mono<AppProperty> getPropertyById(ULong id) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_PROPERTY)
                .where(SECURITY_APP_PROPERTY.ID.eq(id))
                .limit(1))
            .map(e -> e.into(AppProperty.class));
    }

    public Mono<Boolean> deletePropertyById(ULong id) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_PROPERTY)
                .where(SECURITY_APP_PROPERTY.ID.eq(id)))
            .map(e -> e == 1);
    }

    public Map<ULong, Map<String, AppProperty>> convertAttributesToMap(ULong appClientId,
                                                                       List<AppProperty> lst) {

        Map<String, AppProperty> appDefaultProps = new HashMap<>();
        Map<ULong, Map<String, AppProperty>> appClientProps = new HashMap<>();

        for (AppProperty prop : lst) {
            if (prop.getClientId().equals(appClientId)) {
                appDefaultProps.put(prop.getName(), prop);
            }
            if (!appClientProps.containsKey(prop.getClientId()))
                appClientProps.put(prop.getClientId(), new HashMap<>());
            appClientProps.get(prop.getClientId()).put(prop.getName(), prop);
        }

        for (Entry<ULong, Map<String, AppProperty>> entry : appClientProps.entrySet()) {

            if (entry.getKey().equals(appClientId))
                continue;

            for (Entry<String, AppProperty> defaultProp : appDefaultProps.entrySet()) {
                if (!entry.getValue().containsKey(defaultProp.getKey()))
                    entry.getValue().put(defaultProp.getKey(), defaultProp.getValue());
            }
        }

        Map<String, AppProperty> defaultProps = appClientProps.get(appClientId);
        appClientProps.remove(appClientId);

        if (appClientProps.isEmpty() && defaultProps != null && !defaultProps.isEmpty()) {
            appClientProps.put(appClientId, defaultProps);
        }

        return appClientProps;
    }

    public Mono<Boolean> updateProperty(AppProperty property) {
        return Mono.from(this.dslContext.insertInto(SECURITY_APP_PROPERTY)
                .columns(SECURITY_APP_PROPERTY.APP_ID, SECURITY_APP_PROPERTY.CLIENT_ID,
                    SECURITY_APP_PROPERTY.NAME, SECURITY_APP_PROPERTY.VALUE)
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
        return Mono.justOrEmpty(
                this.dslContext.selectCount()
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

            Mono<List<ULong>> urlIds = Flux
                .from(dsl.select(SecurityClientUrl.SECURITY_CLIENT_URL.ID)
                    .from(SecurityClientUrl.SECURITY_CLIENT_URL)
                    .where(SecurityClientUrl.SECURITY_CLIENT_URL.APP_CODE.eq(appCode)))
                .map(Record1::value1).collectList();

            Mono<List<ULong>> permissionIds = Flux
                .from(dsl.select(SecurityPermission.SECURITY_PERMISSION.ID)
                    .from(SecurityPermission.SECURITY_PERMISSION)
                    .where(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(appId)))
                .map(Record1::value1).collectList();

            Mono<List<ULong>> roleIds = Flux
                .from(dsl.select(SecurityPermission.SECURITY_PERMISSION.ID)
                    .from(SecurityPermission.SECURITY_PERMISSION)
                    .where(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(appId)))
                .map(Record1::value1).collectList();

            return FlatMapUtil.flatMapMono(

                () ->

                    Mono.from(dsl.deleteFrom(SECURITY_APP_PROPERTY)
                        .where(SECURITY_APP_PROPERTY.APP_ID.eq(appId))),

                a -> Mono.zip(urlIds, permissionIds, roleIds),

                (a, tuple) -> Flux
                    .from(dsl.select(SecuritySslRequest.SECURITY_SSL_REQUEST.ID)
                        .from(SecuritySslRequest.SECURITY_SSL_REQUEST)
                        .where(SecuritySslRequest.SECURITY_SSL_REQUEST.URL_ID.in(tuple.getT1())))
                    .map(Record1::value1).collectList(),

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

                    Mono.from(dsl.delete(SecurityPermission.SECURITY_PERMISSION).where(
                        SecurityPermission.SECURITY_PERMISSION.ID.in(tuple.getT2())))

                ),

                (a, tuple, requests, x) -> Mono.from(dsl.delete(SecurityApp.SECURITY_APP)
                    .where(SecurityApp.SECURITY_APP.ID.eq(appId))).map(e -> e == 1));
        })).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.deleteEverythingRelated"));
    }

    public Mono<Boolean> createPropertyFromTransport(ULong appId, ULong clientId, String propertyName,
                                                     String propertyValue) {

        return Mono.from(this.dslContext.insertInto(SECURITY_APP_PROPERTY)
                .columns(SECURITY_APP_PROPERTY.APP_ID, SECURITY_APP_PROPERTY.CLIENT_ID,
                    SECURITY_APP_PROPERTY.NAME, SECURITY_APP_PROPERTY.VALUE)
                .values(appId, clientId, propertyName, propertyValue)
                .onDuplicateKeyUpdate()
                .set(SECURITY_APP_PROPERTY.VALUE, propertyValue))
            .map(e -> e == 1);
    }

    public Mono<Page<App>> readAnyAppsPageFilter(org.springframework.data.domain.Pageable pageable,
                                                 AbstractCondition condition, ULong clientId) {

        return FlatMapUtil.flatMapMono(

            () -> super.filter(condition),

            filterCondition -> {

                List<SortField<?>> orderBy = new ArrayList<>();

                pageable.getSort()
                    .forEach(order -> {
                        Field<?> field = this.getField(order.getProperty());
                        if (field != null)
                            orderBy.add(field.sort(
                                order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
                    });

                Condition anyAppCondition = DSL.and(filterCondition, SECURITY_APP.CLIENT_ID.eq(clientId),
                    SECURITY_APP.APP_ACCESS_TYPE.eq(SecurityAppAppAccessType.ANY));

                final Mono<Integer> recordsCount = Mono
                    .from(this.dslContext.selectCount().from(SECURITY_APP).where(anyAppCondition))
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

                return recordsList.flatMap(
                    list -> recordsCount
                        .map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
            }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.readAnyAppsPageFilter"));
    }

    public Mono<AppDependency> addAppDependency(ULong appId, ULong dependentAppId) {

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.insertInto(SECURITY_APP_DEPENDENCY)
                    .columns(SECURITY_APP_DEPENDENCY.APP_ID, SECURITY_APP_DEPENDENCY.DEP_APP_ID)
                    .values(appId, dependentAppId)
                    .onDuplicateKeyIgnore()).map(e -> e == 1),

                done -> Mono.just(new AppDependency().setAppId(appId).setDependentAppId(dependentAppId)))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.addAppDependency"));
    }

    public Mono<Boolean> removeAppDependency(ULong appId, ULong dependentAppId) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_APP_DEPENDENCY)
                .where(SECURITY_APP_DEPENDENCY.APP_ID.eq(appId)
                    .and(SECURITY_APP_DEPENDENCY.DEP_APP_ID.eq(dependentAppId))))
            .map(e -> e == 1);
    }

    public Mono<List<AppDependency>> getAppDependencies(ULong appId) {

        return FlatMapUtil.flatMapMono(

                () -> Flux
                    .from(this.dslContext.select(SECURITY_APP.ID, SECURITY_APP.APP_NAME, SECURITY_APP.APP_CODE)
                        .from(SECURITY_APP_DEPENDENCY)
                        .leftJoin(SECURITY_APP).on(SECURITY_APP_DEPENDENCY.DEP_APP_ID.eq(SECURITY_APP.ID))
                        .where(SECURITY_APP_DEPENDENCY.APP_ID.eq(appId)))
                    .map(e -> new AppDependency().setDependentAppId(e.get(SECURITY_APP.ID))
                        .setDependentAppName(e.get(SECURITY_APP.APP_NAME))
                        .setDependentAppCode(e.get(SECURITY_APP.APP_CODE)))
                    .collectList(),

                dependencies -> {

                    if (dependencies.isEmpty())
                        return Mono.empty();

                    return Mono
                        .from(this.dslContext.select(SECURITY_APP.ID, SECURITY_APP.APP_NAME, SECURITY_APP.APP_CODE)
                            .from(SECURITY_APP)
                            .where(SECURITY_APP.ID.eq(appId)))
                        .map(app -> {

                            dependencies.forEach(e -> e.setAppId(appId).setAppName(app.get(SECURITY_APP.APP_NAME))
                                .setAppCode(app.get(SECURITY_APP.APP_CODE)));

                            return dependencies;
                        });
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDao.deleteEverythingRelated"));
    }

    public Mono<Page<Client>> getAppClients(String appCode, boolean onlyWriteAccess, String name, ULong clientId,
                                            Pageable pageable) {

        throw new UnsupportedOperationException("Unimplemented method 'getAppClients'");
    }

    public Mono<Boolean> noOneHasWriteAccessExcept(String appCode, String clientCode) {
        return Mono.from(this.dslContext.selectCount()
            .from(SECURITY_APP_ACCESS)
            .join(SECURITY_APP).on(SECURITY_APP_ACCESS.APP_ID.eq(SECURITY_APP.ID))
            .join(SECURITY_CLIENT).on(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_CLIENT.ID))
            .where(DSL.and(SECURITY_APP.APP_CODE.eq(appCode),
                SECURITY_CLIENT.CODE.ne(clientCode)))).map(Record1::value1).map(e -> e == 0);
    }
}
