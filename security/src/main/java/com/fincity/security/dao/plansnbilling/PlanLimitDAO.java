package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityPlanLimit.SECURITY_PLAN_LIMIT;

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
import com.fincity.security.dto.plansnbilling.PlanLimit;
import com.fincity.security.jooq.enums.SecurityPlanLimitStatus;
import com.fincity.security.jooq.tables.records.SecurityPlanLimitRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Component
public class PlanLimitDAO extends AbstractUpdatableDAO<SecurityPlanLimitRecord, ULong, PlanLimit> {

    public PlanLimitDAO() {
        super(PlanLimit.class, SECURITY_PLAN_LIMIT, SECURITY_PLAN_LIMIT.ID);
    }

    public Mono<Boolean> deleteLimits(ULong planId) {
        return Mono.from(this.dslContext.update(SECURITY_PLAN_LIMIT)
                        .set(SECURITY_PLAN_LIMIT.STATUS, SecurityPlanLimitStatus.DELETED)
                        .where(SECURITY_PLAN_LIMIT.PLAN_ID.eq(planId)))
                .map(e -> e > 0);
    }

    public Mono<List<PlanLimit>> updateLimits(ULong planId, List<PlanLimit> limits) {

        return FlatMapUtil.flatMapMono(
                        () -> Flux.from(this.dslContext.selectFrom(SECURITY_PLAN_LIMIT).where(SECURITY_PLAN_LIMIT.ID.eq(planId)))
                                .map(rec -> rec.into(PlanLimit.class)).collectMap(PlanLimit::getId),

                        existingMap -> {
                            Set<PlanLimit> limitsForUpdate = new HashSet<>();
                            Set<PlanLimit> limitsForCreate = new HashSet<>();
                            Set<ULong> limitsForDelete = new HashSet<>();

                            for (PlanLimit limit : limits) {
                                if (limit.getId() == null) {
                                    limitsForCreate.add(limit);
                                } else if (existingMap.containsKey(limit.getId())) {
                                    if (existingMap.get(limit.getId()).getStatus() != SecurityPlanLimitStatus.DELETED)
                                        limitsForUpdate.add(limit);
                                } else {
                                    limitsForDelete.add(limit.getId());
                                }
                            }

                            return Mono.just(Tuples.of(limitsForUpdate, limitsForCreate, limitsForDelete));
                        },

                        (existingMap, tup) -> tup.getT3().isEmpty() ? Mono.just(true)
                                : Mono.from(this.dslContext.update(SECURITY_PLAN_LIMIT)
                                        .set(SECURITY_PLAN_LIMIT.STATUS, SecurityPlanLimitStatus.DELETED)
                                        .where(SECURITY_PLAN_LIMIT.ID.in(tup.getT3())))
                                .map(e -> e > 0),

                        (existingMap, tup, deleted) -> {
                            if (tup.getT2().isEmpty()) return Mono.just(true);

                            return Flux.fromIterable(tup.getT2())
                                    .map(limit -> limit.setPlanId(planId))
                                    .flatMap(this::create)
                                    .collectList()
                                    .map(created -> created.size() == tup.getT2().size());
                        },

                        (existingMap, tup, deleted, created) -> {
                            if (tup.getT1().isEmpty()) return Mono.just(true);

                            return Flux.fromIterable(tup.getT1())
                                    .map(limit -> limit.setPlanId(planId))
                                    .flatMap(this::update)
                                    .collectList()
                                    .map(updated -> updated.size() == tup.getT1().size());
                        },

                        (existingMap, tup, deleted, created, updated) -> this.getLimits(planId)
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanLimitDao.updateLimits"));
    }

    public Mono<List<PlanLimit>> getLimits(ULong planId, SecurityPlanLimitStatus... status) {

        Condition condition = SECURITY_PLAN_LIMIT.PLAN_ID.eq(planId);

        if (status != null && status.length > 0) {
            condition = condition.and(SECURITY_PLAN_LIMIT.STATUS.in(status));
        }

        return Flux.from(this.dslContext.selectFrom(SECURITY_PLAN_LIMIT)
                .where(condition)).map(rec -> rec.into(PlanLimit.class)).collectList();
    }

    public Mono<Map<ULong, List<PlanLimit>>> readLimitsMap(List<ULong> planIds) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_PLAN_LIMIT)
                .where(SECURITY_PLAN_LIMIT.PLAN_ID.in(planIds)))
                .map(rec -> rec.into(PlanLimit.class))
                .collect(Collectors.groupingBy(PlanLimit::getPlanId, Collectors.toList()));
    }
}
