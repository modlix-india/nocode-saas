package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientPlan.SECURITY_CLIENT_PLAN;
import static com.fincity.security.jooq.tables.SecurityPlan.SECURITY_PLAN;
import static com.fincity.security.jooq.tables.SecurityPlanApp.SECURITY_PLAN_APP;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AbstractClientCheckDAO;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.jooq.tables.records.SecurityPlanRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class PlanDAO extends AbstractClientCheckDAO<SecurityPlanRecord, ULong, Plan> {

    public PlanDAO() {
        super(Plan.class, SECURITY_PLAN, SECURITY_PLAN.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_PLAN.CLIENT_ID;
    }

    public Mono<Boolean> addApps(ULong planId, List<ULong> appIds) {

        return Flux.fromIterable(appIds)
                .flatMap(appId -> Mono.from(this.dslContext.insertInto(SECURITY_PLAN_APP)
                        .set(SECURITY_PLAN_APP.PLAN_ID, planId)
                        .set(SECURITY_PLAN_APP.APP_ID, appId)
                        .onDuplicateKeyIgnore())
                        .map(e -> e == 1))
                .all(BooleanUtil::safeValueOf);
    }

    public Mono<Boolean> updateApps(ULong planId, List<ULong> appIds) {

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.deleteFrom(SECURITY_PLAN_APP).where(
                        SECURITY_PLAN_APP.PLAN_ID.eq(planId).and(SECURITY_PLAN_APP.APP_ID.notIn(appIds)))),

                count -> this.addApps(planId, appIds))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.updateApps"));
    }

    public Mono<Boolean> removeApps(ULong planId, List<ULong> appIds) {
        return Mono.from(this.dslContext.deleteFrom(SECURITY_PLAN_APP).where(
                SECURITY_PLAN_APP.PLAN_ID.eq(planId).and(SECURITY_PLAN_APP.APP_ID.in(appIds))))
                .map(e -> e == 1);
    }

    @Override
    public Mono<Integer> delete(ULong id) {

        return Mono.from(this.dslContext.update(SECURITY_PLAN).set(SECURITY_PLAN.STATUS, SecurityPlanStatus.DELETED)
                .where(SECURITY_PLAN.ID.eq(id)));
    }

    public Mono<Map<ULong, Collection<ULong>>> readApps(List<ULong> planIds) {
        return Flux.from(this.dslContext.select(SECURITY_PLAN_APP.PLAN_ID, SECURITY_PLAN_APP.APP_ID)
                .from(SECURITY_PLAN_APP)
                .where(SECURITY_PLAN_APP.PLAN_ID.in(planIds)))
                .collectMultimap(rec -> rec.get(SECURITY_PLAN_APP.PLAN_ID), rec -> rec.get(SECURITY_PLAN_APP.APP_ID));
    }

    public Mono<Boolean> removeClientFromPlan(ULong clientId, ULong planId) {
        return Mono.from(this.dslContext.update(SECURITY_CLIENT_PLAN)
                .set(SECURITY_CLIENT_PLAN.END_DATE, LocalDateTime.now()).where(
                        SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId).and(SECURITY_CLIENT_PLAN.PLAN_ID.eq(planId))))
                .map(e -> e == 1);
    }

    public Mono<Boolean> addClientToPlan(ULong clientId, ULong planId, ULong cycleId, LocalDateTime endDate) {

        return Mono.from(this.dslContext.insertInto(SECURITY_CLIENT_PLAN)
                .set(SECURITY_CLIENT_PLAN.CLIENT_ID, clientId)
                .set(SECURITY_CLIENT_PLAN.PLAN_ID, planId)
                .set(SECURITY_CLIENT_PLAN.CYCLE_ID, cycleId)
                .set(SECURITY_CLIENT_PLAN.END_DATE, endDate)).map(e -> e == 1);
    }

    public Mono<Boolean> findConflictPlans(ULong clientId, String urlClientCode, ULong planId) {

        return FlatMapUtil.flatMapMono(

                () -> Flux
                        .from(this.dslContext.select(SECURITY_PLAN_APP.APP_ID).from(SECURITY_PLAN_APP)
                                .where(SECURITY_PLAN_APP.PLAN_ID.eq(planId)))
                        .map(Record1::value1).collect(Collectors.toSet()),

                planApps -> Flux.from(this.dslContext.select(SECURITY_PLAN_APP.APP_ID).from(SECURITY_CLIENT_PLAN)
                        .join(SECURITY_PLAN).on(SECURITY_CLIENT_PLAN.PLAN_ID.eq(SECURITY_PLAN.ID))
                        .join(SECURITY_PLAN_APP).on(SECURITY_PLAN.ID.eq(SECURITY_PLAN_APP.PLAN_ID))
                        .join(SECURITY_CLIENT).on(SECURITY_PLAN.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                        .where(DSL.and(SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId),
                                SECURITY_CLIENT.CODE.eq(urlClientCode),
                                SECURITY_CLIENT_PLAN.END_DATE.gt(LocalDateTime.now()))))
                        .map(Record1::value1).filter(appId -> !planApps.contains(appId)).collectList()
                        .map(apps -> !apps.isEmpty()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.findConflictPlans"));
    }

    public Mono<List<ULong>> readRegistrationPlans(String urlClientCode, String urlAppCode,
            boolean includeMultiAppPlans) {

        return FlatMapUtil.flatMapMono(

                () -> Flux
                        .from(this.dslContext.select(SECURITY_PLAN_APP.PLAN_ID, SECURITY_PLAN_APP.APP_ID)
                                .from(SECURITY_PLAN_APP)
                                .join(SECURITY_PLAN).on(SECURITY_PLAN_APP.PLAN_ID.eq(SECURITY_PLAN.ID))
                                .join(SECURITY_CLIENT).on(SECURITY_PLAN.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                                .join(SECURITY_APP).on(SECURITY_PLAN_APP.APP_ID.eq(SECURITY_APP.ID))
                                .where(DSL.and(
                                        SECURITY_PLAN.FOR_REGISTRATION.eq(ByteUtil.ONE),
                                        SECURITY_CLIENT.CODE.eq(urlClientCode),
                                        SECURITY_APP.APP_CODE.eq(urlAppCode),
                                        SECURITY_PLAN.STATUS.eq(SecurityPlanStatus.ACTIVE))))
                        .collect(Collectors.groupingBy(Record2::value1,
                                Collectors.mapping(Record2::value2, Collectors.toList()))),

                planApps -> Mono.just(planApps.entrySet().stream()
                        .filter(entry -> includeMultiAppPlans || entry.getValue().size() == 1)
                        .map(Map.Entry::getKey)
                        .toList()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.readRegistrationPlans"));
    }

    public Mono<ULong> getDefaultPlanId(ULong appId) {
        return Mono.from(this.dslContext.select(SECURITY_PLAN_APP.PLAN_ID).from(SECURITY_PLAN_APP)
                .join(SECURITY_PLAN).on(SECURITY_PLAN_APP.PLAN_ID.eq(SECURITY_PLAN.ID))
                .where(SECURITY_PLAN_APP.APP_ID.eq(appId).and(SECURITY_PLAN.DEFAULT_PLAN.eq(ByteUtil.ONE)))
                .orderBy(SECURITY_PLAN.UPDATED_AT.desc())
                .limit(1))
                .map(Record1::value1);
    }

    public Mono<ClientPlan> getClientPlan(ULong appId, ULong clientId) {
        return Mono.from(this.dslContext.select(SECURITY_CLIENT_PLAN.fields()).from(SECURITY_CLIENT_PLAN)
                .join(SECURITY_PLAN).on(SECURITY_CLIENT_PLAN.PLAN_ID.eq(SECURITY_PLAN.ID))
                .join(SECURITY_PLAN_APP).on(SECURITY_PLAN_APP.PLAN_ID.eq(SECURITY_PLAN.ID))
                .where(SECURITY_CLIENT_PLAN.CLIENT_ID.eq(clientId).and(SECURITY_PLAN_APP.APP_ID.eq(appId))).limit(1))
                .map(rec -> rec.into(ClientPlan.class));
    }

    public Flux<ULong> getClientsForPlan(ULong planId) {
        return Flux.from(this.dslContext.select(SECURITY_CLIENT_PLAN.CLIENT_ID).from(SECURITY_CLIENT_PLAN)
                .where(SECURITY_CLIENT_PLAN.PLAN_ID.eq(planId)))
                .map(Record1::value1);
    }
}
