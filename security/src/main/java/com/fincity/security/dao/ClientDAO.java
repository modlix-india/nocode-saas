package com.fincity.security.dao;

import static com.fincity.saas.commons.util.StringUtil.*;
import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityClient.*;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.*;
import static com.fincity.security.jooq.tables.SecurityClientUrl.*;
import static com.fincity.security.jooq.tables.SecurityAppAccess.SECURITY_APP_ACCESS;
import static com.fincity.security.jooq.tables.SecurityProfileUser.SECURITY_PROFILE_USER;
import static com.fincity.security.jooq.tables.SecurityProfile.SECURITY_PROFILE;
import static com.fincity.security.jooq.tables.SecurityProfileRole.SECURITY_PROFILE_ROLE;
import static com.fincity.security.jooq.tables.SecurityV2Role.SECURITY_V2_ROLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.tables.SecurityProfileClientRestriction;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class ClientDAO extends AbstractUpdatableDAO<SecurityClientRecord, ULong, Client> {

    protected ClientDAO() {
        super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
    }

    public Mono<Tuple3<String, String, String>> getClientTypeNCode(ULong id) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT.TYPE_CODE, SECURITY_CLIENT.CODE, SECURITY_CLIENT.LEVEL_TYPE)
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.ID.eq(id))
                        .limit(1))
                .take(1)
                .singleOrEmpty()
                .map(r -> Tuples.of(r.value1(), r.value2(), r.value3().toString()));
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> x = super.getSelectJointStep();

                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return x;

                    return x.map(tup -> tup
                            .mapT1(query -> (SelectJoinStep<Record>) query
                                    .leftJoin(SECURITY_CLIENT_HIERARCHY)
                                    .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID
                                            .eq(SECURITY_CLIENT.ID)))
                            .mapT2(query -> query
                                    .leftJoin(SECURITY_CLIENT_HIERARCHY)
                                    .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID
                                            .eq(SECURITY_CLIENT.ID))));
                });
    }

    @Override
    public Mono<Condition> filter(AbstractCondition condition) {

        return super.filter(condition).flatMap(cond -> SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {

                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return cond;

                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                    return DSL.and(cond, ClientHierarchyDAO.getManageClientCondition(clientId));
                }));
    }

    public Mono<Client> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table)
                        .where(this.idField.eq(id))
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Boolean> makeClientActiveIfInActive(ULong clientId) {

        return Mono.from(this.dslContext.update(SECURITY_CLIENT)
                        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.ACTIVE)
                        .where(SECURITY_CLIENT.ID.eq(clientId)
                                .and(SECURITY_CLIENT.STATUS_CODE
                                        .eq(SecurityClientStatusCode.INACTIVE))))
                .map(e -> e > 0);
    }

    public Mono<Boolean> makeClientInActive(ULong clientId) {

        return Mono.from(this.dslContext.update(SECURITY_CLIENT)
                        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.INACTIVE)
                        .where(SECURITY_CLIENT.ID.eq(clientId)
                                .and(SECURITY_CLIENT.STATUS_CODE.ne(SecurityClientStatusCode.DELETED))))
                .map(e -> e > 0);
    }

    public Mono<Client> getClientBy(String clientCode) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT.fields())
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.CODE.eq(clientCode))
                        .limit(1))
                .singleOrEmpty()
                .map(e -> e.into(Client.class));
    }

    public Mono<List<Client>> getClientsBy(List<ULong> clientIds) {

        return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT)
                .where(SECURITY_CLIENT.ID.in(clientIds))).map(e -> e.into(Client.class)).collectList();
    }

    public Flux<ClientUrlPattern> readClientPatterns() {

        return Flux
                .from(this.dslContext
                        .select(SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT.CODE,
                                SECURITY_CLIENT_URL.URL_PATTERN,
                                SECURITY_CLIENT_URL.APP_CODE)
                        .from(SECURITY_CLIENT_URL)
                        .leftJoin(SECURITY_CLIENT)
                        .on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID)))
                .map(e -> new ClientUrlPattern(e.value1()
                        .toString(), e.value2(), e.value3(), e.value4()))
                .map(ClientUrlPattern::makeHostnPort);
    }

    public Mono<String> getValidClientCode(String name) {

        name = removeSpecialCharacters(name);

        String clientCode = name.substring(0, Math.min(name.length(), 5)).toUpperCase();

        return Flux.just(clientCode)
                .expand(e -> Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
                                .from(SECURITY_CLIENT)
                                .where(SECURITY_CLIENT.CODE.eq(e))
                                .limit(1))
                        .map(Record1::value1)
                        .map(x -> {
                            if (x.length() == clientCode.length())
                                return clientCode + "1";

                            int num = Integer.parseInt(x.substring(clientCode.length()))
                                    + 1;
                            return clientCode + num;
                        }))
                .collectList()
                .map(List::getLast);

    }

    public Mono<ULong> getSystemClientId() {

        return Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.TYPE_CODE.eq("SYS"))
                        .limit(1))
                .map(Record1::value1);
    }

    public Mono<Boolean> isClientActive(List<ULong> clientIds) {
        return Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.STATUS_CODE.eq(SecurityClientStatusCode.ACTIVE))
                        .and(SECURITY_CLIENT.ID.in(clientIds)))
                .map(count -> count.value1() > 0);
    }

    public Mono<Boolean> createProfileRestrictions(ULong clientId, List<ULong> profileIds) {

        return Flux.fromIterable(profileIds).flatMap(profileId -> this.dslContext
                        .insertInto(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .columns(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID,
                                SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                        .values(clientId, profileId))
                .collectList().map(List::size).map(e -> e > 0);
    }

    @Override
    protected Condition filterConditionFilter(FilterCondition fc) {

        if (!fc.getField().equals("appId") && !fc.getField().equals("appCode"))
            return super.filterConditionFilter(fc);

        if (fc.getOperator() != FilterConditionOperator.EQUALS && fc.getOperator() != FilterConditionOperator.IN)
            return DSL.trueCondition();

        if (fc.getField().equals("appId")) {

            Condition idCondition1 = fc.getOperator() == FilterConditionOperator.EQUALS ?
                    SECURITY_APP.ID.eq(ULongUtil.valueOf(this.fieldValue(SECURITY_APP.ID, fc.getValue()))) :
                    SECURITY_APP.ID.in(this.multiFieldValue(SECURITY_APP.ID, fc.getValue(), fc.getMultiValue()));

            Condition idCondition2 = fc.getOperator() == FilterConditionOperator.EQUALS ?
                    SECURITY_APP_ACCESS.APP_ID.eq(ULongUtil.valueOf(this.fieldValue(SECURITY_APP_ACCESS.APP_ID, fc.getValue()))) :
                    SECURITY_APP_ACCESS.APP_ID.in(this.multiFieldValue(SECURITY_APP_ACCESS.APP_ID, fc.getValue(), fc.getMultiValue()));

            if (fc.isNegate()) {
                idCondition1 = DSL.not(idCondition1);
                idCondition2 = DSL.not(idCondition2);
            }

            return DSL.or(
                    DSL.exists(DSL.select(DSL.value(1)).from(SECURITY_APP).where(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID).and(idCondition1))),
                    DSL.exists(DSL.select(DSL.value(1)).from(SECURITY_APP_ACCESS).where(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_CLIENT.ID).and(idCondition2)))
            );
        }

        Condition appCodeCondition = fc.getOperator() == FilterConditionOperator.EQUALS ?
                SECURITY_APP.APP_CODE.eq(fc.getValue().toString()) :
                SECURITY_APP.APP_CODE.in(this.multiFieldValue(SECURITY_APP.APP_CODE, fc.getValue(), fc.getMultiValue()));

        if (fc.isNegate()) appCodeCondition = DSL.not(appCodeCondition);

        return DSL.or(
                DSL.exists(DSL.select(DSL.value(1)).from(SECURITY_APP).where(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID).and(appCodeCondition))),
                DSL.exists(DSL.select(DSL.value(1)).from(SECURITY_APP_ACCESS)
                        .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_APP_ACCESS.APP_ID))
                        .where(SECURITY_APP_ACCESS.CLIENT_ID.eq(SECURITY_CLIENT.ID).and(appCodeCondition)))
        );
    }

    public Mono<List<Client>> fillUserCounts(Map<ULong, Client> map, String appCode, String appId) {

        Condition appCondition = DSL.trueCondition();
        if (!StringUtil.safeIsBlank(appCode)) appCondition = SECURITY_APP.APP_CODE.eq(appCode);
        else if (!StringUtil.safeIsBlank(appId)) appCondition = SECURITY_APP.ID.eq(ULongUtil.valueOf(appId));

        SelectConditionStep<Record3<ULong, ULong, SecurityUserStatusCode>> innerQuery = this.dslContext.selectDistinct(SECURITY_USER.ID, SECURITY_USER.CLIENT_ID.as("clientId"), SECURITY_USER.STATUS_CODE.as("statusCode")).from(SECURITY_USER)
                .leftJoin(SECURITY_PROFILE_USER).on(SECURITY_PROFILE_USER.USER_ID.eq(SECURITY_USER.ID))
                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                .where(DSL.and(appCondition, SECURITY_USER.CLIENT_ID.in(map.keySet()), SECURITY_USER.STATUS_CODE.isNotNull()));

        var clientField = DSL.field("clientId", ULong.class);
        var statusField = DSL.field("statusCode", SecurityUserStatusCode.class);

        return Flux.from(this.dslContext.select(clientField, statusField, DSL.count().as("count"))
                        .from(innerQuery)
                        .groupBy(clientField, statusField))
                .collectList()
                .map(counts -> {

                    for (var count : counts) {
                        if (count.value2() == null) continue;
                        switch (count.value2()) {
                            case ACTIVE -> map.get(count.value1()).setActiveUsers(count.value3());
                            case INACTIVE -> map.get(count.value1()).setInactiveUsers(count.value3());
                            case DELETED -> map.get(count.value1()).setDeletedUsers(count.value3());
                            case LOCKED -> map.get(count.value1()).setLockedUsers(count.value3());
                            case PASSWORD_EXPIRED -> map.get(count.value1()).setPasswordExpiredUsers(count.value3());
                        }
                    }

                    return map.values().stream().toList();
                });
    }

    public Mono<Map<ULong, Collection<ULong>>> getOwnersPerClient(Map<ULong, Client> map, String appCode, String appId) {

        Condition appCondition = DSL.trueCondition();
        if (!StringUtil.safeIsBlank(appCode)) appCondition = SECURITY_APP.APP_CODE.eq(appCode);
        else if (!StringUtil.safeIsBlank(appId)) appCondition = SECURITY_APP.ID.eq(ULongUtil.valueOf(appId));

        return Flux.from(this.dslContext.select(SECURITY_USER.CLIENT_ID, SECURITY_USER.ID).from(SECURITY_USER)
                .leftJoin(SECURITY_PROFILE_USER).on(SECURITY_PROFILE_USER.USER_ID.eq(SECURITY_USER.ID))
                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .leftJoin(SECURITY_PROFILE_ROLE).on(SECURITY_PROFILE_ROLE.PROFILE_ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .leftJoin(SECURITY_V2_ROLE).on(SECURITY_V2_ROLE.ID.eq(SECURITY_PROFILE_ROLE.ROLE_ID))
                .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                .where(SECURITY_V2_ROLE.NAME.eq("Owner").and(appCondition))
        ).collectMultimap(Record2::value1, Record2::value2);
    }
}
