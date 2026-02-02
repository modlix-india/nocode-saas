package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityPlanCycle.SECURITY_PLAN_CYCLE;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.plansnbilling.PlanCycle;
import com.fincity.security.jooq.enums.SecurityPlanCycleStatus;
import com.fincity.security.jooq.tables.records.SecurityPlanCycleRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Component
public class PlanCycleDAO extends AbstractUpdatableDAO<SecurityPlanCycleRecord, ULong, PlanCycle> {

    public PlanCycleDAO() {
        super(PlanCycle.class, SECURITY_PLAN_CYCLE, SECURITY_PLAN_CYCLE.ID);
    }

    public Mono<Boolean> deleteCycles(ULong planId) {
        return Mono.from(this.dslContext.update(SECURITY_PLAN_CYCLE)
                .set(SECURITY_PLAN_CYCLE.STATUS, SecurityPlanCycleStatus.DELETED)
                .where(SECURITY_PLAN_CYCLE.PLAN_ID.eq(planId)))
                .map(e -> e > 0);
    }

    public Mono<List<PlanCycle>> updateCycles(ULong planId, List<PlanCycle> cycles) {

        return FlatMapUtil.flatMapMono(
                () -> Flux
                        .from(this.dslContext.selectFrom(SECURITY_PLAN_CYCLE).where(SECURITY_PLAN_CYCLE.ID.eq(planId)))
                        .map(rec -> rec.into(PlanCycle.class)).collectMap(PlanCycle::getId),

                existingMap -> {

                    Set<PlanCycle> cyclesForUpdate = new HashSet<>();
                    Set<PlanCycle> cyclesForCreate = new HashSet<>();
                    Set<ULong> cyclesForDelete = new HashSet<>();

                    for (PlanCycle cycle : cycles) {
                        if (cycle.getId() == null) {
                            cyclesForCreate.add(cycle);
                        } else if (existingMap.containsKey(cycle.getId())) {
                            if (existingMap.get(cycle.getId()).getStatus() != SecurityPlanCycleStatus.DELETED)
                                cyclesForUpdate.add(cycle);
                        } else {
                            cyclesForDelete.add(cycle.getId());
                        }
                    }

                    return Mono.just(Tuples.of(cyclesForUpdate, cyclesForCreate, cyclesForDelete));
                },

                (existingMap, tup) -> tup.getT3().isEmpty() ? Mono.just(true)
                        : Mono.from(this.dslContext.update(SECURITY_PLAN_CYCLE)
                                .set(SECURITY_PLAN_CYCLE.STATUS, SecurityPlanCycleStatus.DELETED)
                                .where(SECURITY_PLAN_CYCLE.ID.in(tup.getT3())))
                                .map(e -> e > 0),

                (existingMap, tup, deleted) -> {

                    if (tup.getT2().isEmpty())
                        return Mono.just(true);

                    return Flux.fromIterable(tup.getT2())
                            .map(cycle -> cycle.setPlanId(planId))
                            .flatMap(this::create)
                            .collectList()
                            .map(created -> created.size() == tup.getT2().size());
                },

                (existingMap, tup, deleted, created) -> {

                    if (tup.getT1().isEmpty())
                        return Mono.just(true);

                    return Flux.fromIterable(tup.getT1())
                            .map(cycle -> cycle.setPlanId(planId))
                            .flatMap(this::update)
                            .collectList()
                            .map(updated -> updated.size() == tup.getT1().size());
                },

                (existingMap, tup, deleted, created, updated) -> this.getCycles(planId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanCycleDao.updateCycles"));
    }

    public Mono<List<PlanCycle>> getCycles(ULong planId, SecurityPlanCycleStatus... status) {

        Condition condition = SECURITY_PLAN_CYCLE.PLAN_ID.eq(planId);

        if (status != null && status.length > 0) {
            condition = condition.and(SECURITY_PLAN_CYCLE.STATUS.in(status));
        }

        return Flux.from(this.dslContext.selectFrom(SECURITY_PLAN_CYCLE)
                .where(condition)).map(rec -> rec.into(PlanCycle.class)).collectList();
    }

    public Mono<Map<ULong, List<PlanCycle>>> readCyclesMap(List<ULong> planIds) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_PLAN_CYCLE)
                .where(SECURITY_PLAN_CYCLE.PLAN_ID.in(planIds)))
                .map(rec -> rec.into(PlanCycle.class))
                .collect(Collectors.groupingBy(PlanCycle::getPlanId, Collectors.toList()));
    }
}
