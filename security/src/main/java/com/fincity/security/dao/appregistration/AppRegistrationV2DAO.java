package com.fincity.security.dao.appregistration;

import static com.fincity.security.jooq.tables.SecurityAppRegAccess.*;
import static com.fincity.security.jooq.tables.SecurityAppRegFileAccess.*;
import static com.fincity.security.jooq.tables.SecurityAppRegUserRoleV2.*;
import static com.fincity.security.jooq.tables.SecurityAppRegUserProfile.*;
import static com.fincity.security.jooq.tables.SecurityAppRegDepartment.*;
import static com.fincity.security.jooq.tables.SecurityAppRegDesignation.*;
import static com.fincity.security.jooq.tables.SecurityAppRegProfileRestriction.*;
import static com.fincity.security.jooq.tables.SecurityAppRegProfileRestriction.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.dto.appregistration.AppRegistrationFileAccess;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityAppRegAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegDepartmentLevel;
import com.fincity.security.jooq.enums.SecurityAppRegDesignationLevel;
import com.fincity.security.jooq.enums.SecurityAppRegFileAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegProfileRestrictionLevel;
import com.fincity.security.jooq.enums.SecurityAppRegUserProfileLevel;
import com.fincity.security.jooq.enums.SecurityAppRegUserRoleV2Level;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class AppRegistrationV2DAO {

    private DSLContext dslContext;

    public AppRegistrationV2DAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<? extends AbstractAppRegistration> create(AppRegistrationObjectType type,
            AbstractAppRegistration entity) {

        Record r = this.dslContext.newRecord(type.table, entity);

        return Mono
                .from(this.dslContext.insertInto(type.table).set(r)
                        .returningResult(type.table.field("ID", ULong.class)))
                .map(Record1::value1).flatMap(id -> this.getById(type, id));
    }

    public Mono<? extends AbstractAppRegistration> getById(AppRegistrationObjectType type, ULong id) {
        return Mono.from(this.dslContext.selectFrom(type.table).where(type.table.field("ID", ULong.class).eq(id)))
                .map(r -> r.into(type.pojoClass));
    }

    public Mono<Boolean> delete(AppRegistrationObjectType type, ULong id) {
        return Mono.from(this.dslContext.deleteFrom(type.table).where(type.table.field("ID", ULong.class).eq(id)))
                .map(r -> r > 0);
    }

    public Mono<Page<AbstractAppRegistration>> get(AppRegistrationObjectType type, ULong appId,
            ULong clientId, String clientType, ClientLevelType level, String businessType, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        if (appId != null)
            conditions.add(type.table.field("APP_ID", ULong.class).eq(appId));

        if (clientId != null)
            conditions.add(type.table.field("CLIENT_ID", ULong.class).eq(clientId));

        if (clientType != null)
            conditions.add(type.table.field("CLIENT_TYPE", String.class).eq(clientType));

        if (level != null)
            conditions.add(type.table.field("LEVEL", ClientLevelType.class).eq(level));

        if (businessType != null)
            conditions.add(type.table.field("BUSINESS_TYPE", String.class).eq(businessType));

        Condition condition = DSL.and(conditions);

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.selectFrom(type.table).where(condition)
                        .orderBy(type.table.field("CREATED_AT").desc()).limit(pageable.getPageSize())
                        .offset(pageable.getOffset())).map(e -> e.into(type.pojoClass))
                        .map(AbstractAppRegistration.class::cast).collectList(),

                lst -> Mono.from(this.dslContext.selectCount().from(type.table).where(condition))
                        .map(Record1::value1).map(e -> (long) e),

                (lst, count) -> {
                    if (lst.isEmpty())
                        return Mono.just(Page.<AbstractAppRegistration>empty(pageable));

                    return Mono.just(
                            PageableExecutionUtils.<AbstractAppRegistration>getPage(lst, pageable, () -> count));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationV2DAO.get"));
    }

    public Mono<List<Tuple2<ULong, Boolean>>> getAppIdsForRegistration(ULong appId, ULong appClientId,
            ULong urlClientId,
            String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_ACCESS.APP_ID.eq(appId),
                SECURITY_APP_REG_ACCESS.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_ACCESS.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_ACCESS.LEVEL.eq(level.to(SecurityAppRegAccessLevel.class)),
                SECURITY_APP_REG_ACCESS.BUSINESS_TYPE.eq(businessType));

        SelectConditionStep<Record3<ULong, ULong, Byte>> query = this.dslContext
                .select(SECURITY_APP_REG_ACCESS.CLIENT_ID,
                        SECURITY_APP_REG_ACCESS.ALLOW_APP_ID, SECURITY_APP_REG_ACCESS.WRITE_ACCESS)
                .from(SECURITY_APP_REG_ACCESS)
                .where(condition);

        return Flux.from(query).collectList().map(e -> {

            if (e.isEmpty())
                return List.of(Tuples.of(appId, false));

            Map<ULong, List<Tuple2<ULong, Boolean>>> map = new HashMap<>();

            for (var r : e) {
                ULong clientId = r.get(SECURITY_APP_REG_ACCESS.CLIENT_ID);
                Tuple2<ULong, Boolean> tup = Tuples.of(r.get(SECURITY_APP_REG_ACCESS.ALLOW_APP_ID),
                        r.get(SECURITY_APP_REG_ACCESS.WRITE_ACCESS).equals(ByteUtil.ONE));
                if (!map.containsKey(clientId))
                    map.put(clientId, new ArrayList<>());
                map.get(clientId).add(tup);
            }

            List<Tuple2<ULong, Boolean>> list = map.getOrDefault(urlClientId, map.get(appClientId));
            if (list == null || list.isEmpty())
                return List.of(Tuples.of(appId, false));

            Tuple2<ULong, Boolean> found = null;
            for (var t : list) {
                if (!t.getT1().equals(appId))
                    continue;
                found = t;
                list.remove(t);
                break;
            }

            if (found == null)
                found = Tuples.of(appId, false);

            list.add(0, found);

            return list;
        });
    }

    public Mono<List<AppRegistrationFileAccess>> getFileAccessForRegistration(ULong appId, ULong appClientId,
            ULong urlClientId,
            String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_FILE_ACCESS.APP_ID.eq(appId),
                SECURITY_APP_REG_FILE_ACCESS.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_FILE_ACCESS.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_FILE_ACCESS.LEVEL.eq(level.to(SecurityAppRegFileAccessLevel.class)),
                SECURITY_APP_REG_FILE_ACCESS.BUSINESS_TYPE.eq(businessType));

        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_REG_FILE_ACCESS).where(condition))
                .map(e -> e.into(AppRegistrationFileAccess.class)).collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, List<AppRegistrationFileAccess>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId1 = r.getClientId();
                        List<AppRegistrationFileAccess> list = map.getOrDefault(clientId1, new ArrayList<>());
                        list.add(r);
                        map.put(clientId1, list);
                    }

                    return map.getOrDefault(urlClientId, map.get(appClientId));
                });
    }

    public Mono<List<AppRegistrationDepartment>> getDepartmentsForRegistration(ULong appId, ULong appClientId,
            ULong urlClientId, String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_DEPARTMENT.APP_ID.eq(appId),
                SECURITY_APP_REG_DEPARTMENT.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_DEPARTMENT.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_DEPARTMENT.LEVEL.eq(level.to(SecurityAppRegDepartmentLevel.class)),
                SECURITY_APP_REG_DEPARTMENT.BUSINESS_TYPE.eq(businessType));

        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_REG_DEPARTMENT).where(condition))
                .map(e -> e.into(AppRegistrationDepartment.class)).collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, List<AppRegistrationDepartment>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId1 = r.getClientId();
                        List<AppRegistrationDepartment> list = map.getOrDefault(clientId1, new ArrayList<>());
                        list.add(r);
                        map.put(clientId1, list);
                    }

                    return map.getOrDefault(urlClientId, map.get(appClientId));
                });
    }

    public Mono<List<AppRegistrationDesignation>> getDesignationsForRegistration(ULong appId, ULong appClientId,
            ULong urlClientId, String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_DESIGNATION.APP_ID.eq(appId),
                SECURITY_APP_REG_DESIGNATION.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_DESIGNATION.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_DESIGNATION.LEVEL.eq(level.to(SecurityAppRegDesignationLevel.class)),
                SECURITY_APP_REG_DESIGNATION.BUSINESS_TYPE.eq(businessType));

        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_REG_DESIGNATION).where(condition))
                .map(e -> e.into(AppRegistrationDesignation.class)).collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, List<AppRegistrationDesignation>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId1 = r.getClientId();
                        List<AppRegistrationDesignation> list = map.getOrDefault(clientId1, new ArrayList<>());
                        list.add(r);
                        map.put(clientId1, list);
                    }

                    return map.getOrDefault(urlClientId, map.get(appClientId));
                });
    }

    public Mono<List<ULong>> getRoleIdsForRegistration(ULong appId, ULong appClientId, ULong urlClientId,
            String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_USER_ROLE_V2.APP_ID.eq(appId),
                SECURITY_APP_REG_USER_ROLE_V2.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_USER_ROLE_V2.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_USER_ROLE_V2.LEVEL.eq(level.to(SecurityAppRegUserRoleV2Level.class)),
                SECURITY_APP_REG_USER_ROLE_V2.BUSINESS_TYPE.eq(businessType));

        return Flux
                .from(this.dslContext
                        .select(SECURITY_APP_REG_USER_ROLE_V2.CLIENT_ID, SECURITY_APP_REG_USER_ROLE_V2.ROLE_ID)
                        .from(SECURITY_APP_REG_USER_ROLE_V2).where(condition))
                .collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, Set<ULong>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId = r.get(SECURITY_APP_REG_USER_ROLE_V2.CLIENT_ID);
                        ULong roleId = r.get(SECURITY_APP_REG_USER_ROLE_V2.ROLE_ID);

                        Set<ULong> list = map.getOrDefault(clientId, new HashSet<>());
                        list.add(roleId);
                        map.put(clientId, list);
                    }

                    return new ArrayList<>(map.getOrDefault(urlClientId, map.get(appClientId)));
                });
    }

    public Mono<List<ULong>> getProfileIdsForRegistration(ULong appId, ULong appClientId, ULong urlClientId,
            String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_USER_PROFILE.APP_ID.eq(appId),
                SECURITY_APP_REG_USER_PROFILE.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_USER_PROFILE.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_USER_PROFILE.LEVEL.eq(level.to(SecurityAppRegUserProfileLevel.class)),
                SECURITY_APP_REG_USER_PROFILE.BUSINESS_TYPE.eq(businessType));

        return Flux
                .from(this.dslContext
                        .select(SECURITY_APP_REG_USER_PROFILE.CLIENT_ID, SECURITY_APP_REG_USER_PROFILE.PROFILE_ID)
                        .from(SECURITY_APP_REG_USER_PROFILE).where(condition))
                .collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, Set<ULong>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId = r.get(SECURITY_APP_REG_USER_PROFILE.CLIENT_ID);
                        ULong profileId = r.get(SECURITY_APP_REG_USER_PROFILE.PROFILE_ID);

                        Set<ULong> list = map.getOrDefault(clientId, new HashSet<>());
                        list.add(profileId);
                        map.put(clientId, list);
                    }

                    return new ArrayList<>(map.getOrDefault(urlClientId, map.get(appClientId)));
                });
    }

    public Mono<List<ULong>> getProfileRestrictionIdsForRegistration(ULong appId, ULong appClientId, ULong urlClientId,
            String clientType, ClientLevelType level, String businessType) {

        Condition condition = DSL.and(SECURITY_APP_REG_PROFILE_RESTRICTION.APP_ID.eq(appId),
                SECURITY_APP_REG_PROFILE_RESTRICTION.CLIENT_ID.in(appClientId, urlClientId),
                SECURITY_APP_REG_PROFILE_RESTRICTION.CLIENT_TYPE.eq(clientType),
                SECURITY_APP_REG_PROFILE_RESTRICTION.LEVEL.eq(level.to(SecurityAppRegProfileRestrictionLevel.class)),
                SECURITY_APP_REG_PROFILE_RESTRICTION.BUSINESS_TYPE.eq(businessType));

        return Flux
                .from(this.dslContext
                        .select(SECURITY_APP_REG_PROFILE_RESTRICTION.CLIENT_ID,
                                SECURITY_APP_REG_PROFILE_RESTRICTION.PROFILE_ID)
                        .from(SECURITY_APP_REG_PROFILE_RESTRICTION).where(condition))
                .collectList().map(e -> {

                    if (e.isEmpty())
                        return List.of();

                    Map<ULong, Set<ULong>> map = new HashMap<>();

                    for (var r : e) {
                        ULong clientId = r.get(SECURITY_APP_REG_PROFILE_RESTRICTION.CLIENT_ID);
                        ULong profileId = r.get(SECURITY_APP_REG_PROFILE_RESTRICTION.PROFILE_ID);

                        Set<ULong> list = map.getOrDefault(clientId, new HashSet<>());
                        list.add(profileId);
                        map.put(clientId, list);
                    }

                    return new ArrayList<>(map.getOrDefault(urlClientId, map.get(appClientId)));
                });
    }
}