package com.fincity.security.service.plansnbilling;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.plansnbilling.PlanCycleDAO;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dao.plansnbilling.PlanLimitDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.jooq.tables.records.SecurityPlanRecord;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class PlanService extends AbstractJOOQUpdatableDataService<SecurityPlanRecord, ULong, Plan, PlanDAO> {

    private final PlanCycleDAO planCycleDAO;
    private final PlanLimitDAO planLimitDAO;

    private final ClientService clientService;
    private final AppService appService;

    private final SecurityMessageResourceService messageResourceService;

    public PlanService(PlanCycleDAO planCycleDAO, PlanLimitDAO planLimitDAO, ClientService clientService,
            AppService appService, SecurityMessageResourceService messageResourceService) {
        this.planCycleDAO = planCycleDAO;
        this.planLimitDAO = planLimitDAO;
        this.clientService = clientService;
        this.appService = appService;
        this.messageResourceService = messageResourceService;
    }

    @PreAuthorize("hasAuthority('Authorities.Plan_CREATE')")
    @Override
    public Mono<Plan> create(Plan entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getClientId() == null) {
                        entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                        return Mono.just(true);
                    }

                    return this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                            entity.getClientId()).filter(BooleanUtil::safeValueOf);
                },

                (ca, managed) -> this.appAccesses(entity),

                (ca, managed, hasWriteAccess) -> super.create(entity),

                (ca, managed, hasWriteAccess, created) -> entity.getApps() == null || entity.getApps().isEmpty()
                        ? Mono.just(true)
                        : this.dao.addApps(created.getId(), entity.getApps().stream().map(App::getId).toList()),

                (ca, managed, hasWriteAccess, created, appsAdded) -> {

                    created.setApps(entity.getApps());

                    if (entity.getCycles() == null || entity.getCycles().isEmpty())
                        return Mono.just(created);

                    return Flux.fromIterable(entity.getCycles())
                            .map(cycle -> cycle.setPlanId(created.getId()))
                            .flatMap(this.planCycleDAO::create).collectList()
                            .map(created::setCycles);
                },

                (ca, managed, hasWriteAccess, created, appsAdded, cyclesAdded) -> {

                    if (entity.getLimits() == null || entity.getLimits().isEmpty())
                        return Mono.just(created);

                    return Flux.fromIterable(entity.getLimits())
                            .map(limit -> limit.setPlanId(created.getId()))
                            .flatMap(this.planLimitDAO::create).collectList()
                            .map(created::setLimits);
                })
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, "Plan")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.create"));
    }

    private Mono<Boolean> appAccesses(Plan entity) {
        if (entity.getApps() == null || entity.getApps().isEmpty())
            return Mono.just(true);

        return Flux.fromIterable(entity.getApps())
                .flatMap(app -> this.appService.hasWriteAccess(app.getId(), entity.getClientId()))
                .all(BooleanUtil::safeValueOf)
                .filter(BooleanUtil::safeValueOf);
    }

    @PreAuthorize("hasAuthority('Authorities.Plan_UPDATE')")
    @Override
    public Mono<Plan> update(Plan entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appAccesses(entity),

                (ca, hasWriteAccess) -> super.update(entity),

                (ca, hasWriteAccess, updated) -> entity.getApps() == null || entity.getApps().isEmpty()
                        ? Mono.just(true)
                        : this.dao.updateApps(updated.getId(), entity.getApps().stream().map(App::getId).toList()),

                (ca, hasWriteAccess, updated, appsAdded) -> {

                    updated.setApps(entity.getApps());

                    if (entity.getCycles() == null || entity.getCycles().isEmpty())
                        return this.planCycleDAO.deleteCycles(updated.getId()).map(removed -> updated.setCycles(null));

                    return this.planCycleDAO.updateCycles(updated.getId(), entity.getCycles()).map(updated::setCycles);
                },

                (ca, hasWriteAccess, updated, appsAdded, cyclesAdded) -> {

                    if (entity.getLimits() == null || entity.getLimits().isEmpty())
                        return this.planLimitDAO.deleteLimits(cyclesAdded.getId())
                                .map(removed -> cyclesAdded.setLimits(null));

                    return this.planLimitDAO.updateLimits(cyclesAdded.getId(), entity.getLimits())
                            .map(cyclesAdded::setLimits);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.update"));
    }

    @Override
    protected Mono<Plan> updatableEntity(Plan entity) {

        return FlatMapUtil.flatMapMono(

                () -> ((PlanService) AopContext.currentProxy()).read(entity.getId()),

                existing -> {
                    existing.setName(entity.getName());
                    existing.setDescription(entity.getDescription());
                    existing.setStatus(entity.getStatus());
                    existing.setFeatures(entity.getFeatures());
                    existing.setApps(entity.getApps());
                    existing.setCycles(entity.getCycles());
                    existing.setLimits(entity.getLimits());
                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.updatableEntity"));
    }

    @PreAuthorize("hasAuthority('Authorities.Plan_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.read(id),

                (ca, existing) -> this.clientService
                        .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), existing.getClientId())
                        .filter(BooleanUtil::safeValueOf),

                (ca, existing, hasAccess) -> this.dao.delete(id),

                (ca, existing, hasAccess, deleted) -> Mono
                        .zip(this.planCycleDAO.deleteCycles(id), this.planLimitDAO.deleteLimits(id))
                        .map(x -> deleted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.delete"));
    }

    @PreAuthorize("hasAuthority('Authorities.Plan_READ')")
    @Override
    public Mono<Plan> read(ULong id) {
        return super.read(id).flatMap(p -> this.readOthers(List.of(p))).map(List::getFirst);
    }

    public Mono<Plan> readInternal(ULong id) {
        return super.read(id).flatMap(p -> this.readOthers(List.of(p))).map(List::getFirst);
    }

    @PreAuthorize("hasAuthority('Authorities.Plan_READ')")
    @Override
    public Mono<Page<Plan>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition).flatMap(page -> this.readOthers(page.getContent())
                .map(plans -> PageableExecutionUtils.getPage(plans, pageable, page::getTotalElements)));
    }

    public Mono<List<Plan>> readOthers(List<Plan> plans) {

        return FlatMapUtil.flatMapMono(

                () -> Flux.fromIterable(plans).map(Plan::getId).collectList(),

                planIds -> this.dao.readApps(planIds).flatMapIterable(Map::entrySet)
                        .flatMap(entry -> Flux.fromIterable(entry.getValue()).flatMap(this.appService::getAppById)
                                .collectList().map(apps -> Tuples.of(entry.getKey(), apps)))
                        .collectMap(Tuple2::getT1, Tuple2::getT2),

                (planIds, appMap) -> this.planCycleDAO.readCyclesMap(planIds),

                (planIds, appMap, cycleMap) -> this.planLimitDAO.readLimitsMap(planIds),

                (planIds, appMap, cycleMap, limitMap) -> Flux.fromIterable(plans).map(p -> {
                    p.setApps(appMap.get(p.getId()));
                    p.setCycles(cycleMap.get(p.getId()));
                    p.setLimits(limitMap.get(p.getId()));
                    return p;
                }).collectList()).contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.readOthers"));
    }

    public Mono<Boolean> addPlanAndCyCle(ULong clientId, String urlClientCode, ULong planId, ULong cycleId,
            LocalDateTime endDate) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
                        .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess) -> this.dao.findConflictPlans(clientId, urlClientCode, planId)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.PLAN_CONFLICT_PLAN_ALREADY_EXISTS)),

                (ca, hasAccess, conflictPlans) -> this.clientService.getClientBy(urlClientCode),

                (ca, hasAccess, conflictPlans, urlClient) -> this.readInternal(planId)
                        .filter(plan -> plan.getClientId().equals(urlClient.getId()))
                        .filter(plan -> plan.getCycles() != null
                                && plan.getCycles().stream().anyMatch(cycle -> cycle.getId().equals(cycleId))),

                (ca, hasAccess, conflictPlans, urlClient, plan) -> this.dao.addClientToPlan(clientId, planId, cycleId,
                        endDate))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, "Plan")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.addPlanAndCyCle"));
    }

    public Mono<Boolean> removeClientFromPlan(ULong clientId, ULong planId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
                        .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess) -> this.dao.removeClientFromPlan(clientId, planId))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, "Plan")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PlanService.removeClientFromPlan"));
    }
}
