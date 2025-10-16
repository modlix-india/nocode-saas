package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityPlan.SECURITY_PLAN;
import static com.fincity.security.jooq.tables.SecurityPlanApp.SECURITY_PLAN_APP;
import static com.fincity.security.jooq.tables.SecurityClientPlan.SECURITY_CLIENT_PLAN;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AbstractClientCheckDAO;
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
            
            count -> this.addApps(planId, appIds)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanDao.updateApps"));
    }

    public Mono<Boolean> removeApps(ULong planId, List<ULong> appIds) {
        return Mono.from(this.dslContext.deleteFrom(SECURITY_PLAN_APP).where(
            SECURITY_PLAN_APP.PLAN_ID.eq(planId).and(SECURITY_PLAN_APP.APP_ID.in(appIds))))
        .map(e -> e == 1);
    }

    @Override
    public Mono<Integer> delete(ULong id) {

        return Mono.from(this.dslContext.update(SECURITY_PLAN).set(SECURITY_PLAN.STATUS, SecurityPlanStatus.DELETED).where(SECURITY_PLAN.ID.eq(id)));
    }

    public Mono<Map<ULong, Collection<ULong>>> readApps(List<ULong> planIds) {
        return Flux.from(this.dslContext.select(SECURITY_PLAN_APP.PLAN_ID, SECURITY_PLAN_APP.APP_ID)
        .from(SECURITY_PLAN_APP)
        .where(SECURITY_PLAN_APP.PLAN_ID.in(planIds)))
        .collectMultimap(rec -> rec.get(SECURITY_PLAN_APP.PLAN_ID), rec -> rec.get(SECURITY_PLAN_APP.APP_ID));
        }


    public Mono<Boolean> addClientToPlan(ULong clientId, ULong planId, ULong cycleId, LocalDateTime endDate) {
        // Here we need to check if the client has any plan already whit the same apps and throw an exception,
        // Also if the the apps are exactly same then end that plan and add this plan.

        return Mono.just(true);
    }
}
