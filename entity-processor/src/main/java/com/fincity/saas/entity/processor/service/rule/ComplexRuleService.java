package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.ComplexRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.base.IConditionRuleService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ComplexRuleService extends BaseRuleService<EntityProcessorComplexRulesRecord, ComplexRule, ComplexRuleDAO>
        implements IConditionRuleService<ComplexCondition, ComplexRule, ComplexRuleService> {

    private static final String COMPLEX_RULE = "complexRule";

    private SimpleRuleService simpleRuleService;
    private SimpleComplexRuleRelationService simpleComplexRuleRelationService;

    @Lazy
    @Autowired
    private void setSimpleRuleService(SimpleRuleService simpleRuleService) {
        this.simpleRuleService = simpleRuleService;
    }

    @Lazy
    @Autowired
    private void setSimpleComplexRuleRelationService(
            SimpleComplexRuleRelationService simpleComplexRuleRelationService) {
        this.simpleComplexRuleRelationService = simpleComplexRuleRelationService;
    }

    @Override
    protected String getCacheName() {
        return COMPLEX_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.COMPLEX_RULE;
    }

    public Mono<Integer> deleteRule(ULong entityId, EntitySeries entitySeries) {
        return this.dao
                .readByEntityId(entityId, entitySeries)
                .flatMap(this::deleteComplexRule)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.deleteRule"));
    }

    private Mono<Integer> deleteComplexRule(ComplexRule complexRule) {
        if (complexRule.isHasComplexChild()) {
            return FlatMapUtil.flatMapMono(
                            () -> this.dao
                                    .readByParentConditionId(complexRule.getId())
                                    .collectList(),
                            childRules -> {
                                if (childRules.isEmpty()) return this.deleteComplexRuleAndRelations(complexRule);

                                return Flux.fromIterable(childRules)
                                        .flatMap(this::deleteComplexRule)
                                        .reduce(0, Integer::sum)
                                        .flatMap(count -> deleteComplexRuleAndRelations(complexRule));
                            })
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.deleteComplexRule"));
        }

        return this.deleteComplexRuleAndRelations(complexRule)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.deleteComplexRule"));
    }

    private Mono<Integer> deleteComplexRuleAndRelations(ComplexRule complexRule) {

        if (!complexRule.isHasSimpleChild())
            return this.delete(complexRule)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.deleteComplexRuleAndRelations"));

        return this.simpleComplexRuleRelationService
                .deleteByComplexRuleId(complexRule.getId())
                .then(this.simpleRuleService.deleteByComplexRuleId(complexRule.getId()))
                .then(this.delete(complexRule))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.deleteComplexRuleAndRelations"));
    }

    private Mono<Integer> delete(ComplexRule complexRule) {
        return FlatMapUtil.flatMapMono(() -> this.delete(complexRule.getId()), deleted -> this.evictCache(complexRule)
                        .map(evicted -> deleted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.delete"));
    }

    @Override
    protected Mono<ComplexRule> updatableEntity(ComplexRule complexRule) {
        return super.updatableEntity(complexRule)
                .flatMap(existing -> {
                    existing.setParentConditionId(complexRule.getParentConditionId());
                    existing.setLogicalOperator(complexRule.getLogicalOperator());
                    existing.setHasComplexChild(complexRule.isHasComplexChild());
                    existing.setHasSimpleChild(complexRule.isHasSimpleChild());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.updatableEntity"));
    }

    @Override
    public Mono<ComplexRule> createForCondition(
            ULong entityId, EntitySeries entitySeries, ProcessorAccess access, ComplexCondition condition) {
        return FlatMapUtil.flatMapMono(
                        () -> this.createComplexRuleInternal(entityId, entitySeries, access, condition, null),
                        complexRule -> this.cacheService
                                .evict(this.getCacheName(), this.getCacheKey(entityId, entitySeries))
                                .map(evicted -> complexRule))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.createForCondition"));
    }

    @Override
    public Mono<AbstractCondition> getCondition(ULong entityId, EntitySeries entitySeries) {
        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.getConditionInternal(entityId, entitySeries),
                        this.getCacheKey(entityId, entitySeries))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.getCondition"));
    }

    private Mono<AbstractCondition> getConditionInternal(ULong entityId, EntitySeries entitySeries) {
        return this.dao
                .readByEntityId(entityId, entitySeries)
                .flatMap(complexRule -> {
                    if (!complexRule.isHasComplexChild() && !complexRule.isHasSimpleChild())
                        return Mono.just(ComplexRule.toCondition(complexRule, List.of()));

                    Flux<AbstractCondition> complexChildrenConditions = complexRule.isHasComplexChild()
                            ? this.dao
                                    .readByParentConditionId(complexRule.getId())
                                    .flatMap(childRule ->
                                            this.getCondition(childRule.getId(), childRule.getEntitySeries()))
                            : Flux.empty();

                    Flux<AbstractCondition> simpleChildrenConditions = complexRule.isHasSimpleChild()
                            ? this.simpleRuleService.getConditionByComplexRule(complexRule.getId())
                            : Flux.empty();

                    return Flux.merge(complexChildrenConditions, simpleChildrenConditions)
                            .collectList()
                            .map(conditions -> ComplexRule.toCondition(complexRule, conditions));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.getConditionInternal"));
    }

    private Flux<Void> processConditions(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            List<AbstractCondition> conditions,
            ULong complexRuleId) {
        return Flux.fromIterable(conditions).index().flatMap(tuple -> {
            AbstractCondition condition = tuple.getT2();
            int index = tuple.getT1().intValue();

            if (condition instanceof FilterCondition filterCondition) {
                return processSimpleCondition(ruleId, entitySeries, access, filterCondition, complexRuleId, index);
            } else if (condition instanceof ComplexCondition complexCondition) {
                return processComplexCondition(ruleId, entitySeries, access, complexCondition, complexRuleId);
            }

            return Mono.empty();
        });
    }

    private Mono<Void> processSimpleCondition(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            FilterCondition condition,
            ULong complexRuleId,
            int index) {
        Mono<Void> result = this.simpleRuleService
                .createForConditionWithParent(ruleId, entitySeries, access, condition, complexRuleId, index)
                .then(Mono.empty());
        return Mono.deferContextual(ctx ->
                result.contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.processSimpleCondition")));
    }

    private Mono<Void> processComplexCondition(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            ComplexCondition condition,
            ULong complexRuleId) {
        Mono<Void> result = this.createForConditionWithParent(ruleId, entitySeries, access, condition, complexRuleId)
                .then(Mono.empty());
        return Mono.deferContextual(ctx ->
                result.contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.processComplexCondition")));
    }

    private Mono<ComplexRule> createForConditionWithParent(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            ComplexCondition condition,
            ULong parentId) {
        return this.createComplexRuleInternal(ruleId, entitySeries, access, condition, parentId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.createForConditionWithParent"));
    }

    private Mono<ComplexRule> createComplexRuleInternal(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            ComplexCondition condition,
            ULong parentId) {
        if (condition.isEmpty()) return Mono.empty();

        ComplexRule complexRule = ComplexRule.fromCondition(ruleId, entitySeries, condition);

        if (parentId != null) complexRule.setParentConditionId(parentId);

        List<AbstractCondition> conditions = condition.getConditions();
        this.addChildrenInfo(complexRule, conditions);

        return this.createInternal(access, complexRule)
                .flatMap(cComplexRule -> {
                    if (conditions == null || conditions.isEmpty()) return Mono.just(cComplexRule);

                    return this.processConditions(ruleId, entitySeries, access, conditions, cComplexRule.getId())
                            .then(Mono.just(cComplexRule));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ComplexRuleService.createComplexRuleInternal"));
    }

    private void addChildrenInfo(ComplexRule complexRule, List<AbstractCondition> conditions) {

        boolean hasComplexChild = false;
        boolean hasSimpleChild = false;

        if (conditions != null && !conditions.isEmpty()) {
            for (AbstractCondition childCondition : conditions) {
                hasComplexChild |= childCondition instanceof ComplexCondition;
                hasSimpleChild |= childCondition instanceof FilterCondition;

                if (hasComplexChild && hasSimpleChild) break;
            }
        }

        complexRule.setHasComplexChild(hasComplexChild);
        complexRule.setHasSimpleChild(hasSimpleChild);
    }
}
