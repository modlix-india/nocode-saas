package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.SimpleRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.base.IConditionRuleService;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimpleRuleService extends BaseRuleService<EntityProcessorSimpleRulesRecord, SimpleRule, SimpleRuleDAO>
        implements IConditionRuleService<FilterCondition, SimpleRule, SimpleRuleService> {

    private static final String SIMPLE_RULE = "simpleRule";

    private SimpleComplexRuleRelationService simpleComplexRuleRelationService;

    @Lazy
    @Autowired
    private void setSimpleComplexRuleRelationService(
            SimpleComplexRuleRelationService simpleComplexRuleRelationService) {
        this.simpleComplexRuleRelationService = simpleComplexRuleRelationService;
    }

    @Override
    protected String getCacheName() {
        return SIMPLE_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_RULE;
    }

    @Override
    protected Mono<SimpleRule> updatableEntity(SimpleRule entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            existing.setField(entity.getField());
            existing.setComparisonOperator(entity.getComparisonOperator());
            existing.setValue(entity.getValue());
            existing.setValueField(entity.isValueField());
            existing.setToValueField(entity.isToValueField());
            existing.setMatchOperator(entity.getMatchOperator());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<SimpleRule> createForCondition(
            ULong entityId, EntitySeries entitySeries, ProcessorAccess access, FilterCondition condition) {
        return FlatMapUtil.flatMapMono(
                () -> this.createForCondition(entityId, entitySeries, access, Boolean.FALSE, condition),
                simpleRule -> this.cacheService
                        .evict(this.getCacheName(), this.getCacheKey(entityId, entitySeries))
                        .map(evicted -> simpleRule));
    }

    public Mono<SimpleRule> createForCondition(
            ULong entityId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            boolean hasParent,
            FilterCondition condition) {
        SimpleRule simpleRule =
                SimpleRule.fromCondition(entityId, entitySeries, condition).setHasParent(hasParent);
        return super.createInternal(access, simpleRule);
    }

    @Override
    public Mono<AbstractCondition> getCondition(ULong entityId, EntitySeries entitySeries, boolean hasParent) {

        if (hasParent) return Mono.empty();

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getConditionInternal(entityId, entitySeries, hasParent),
                this.getCacheKey(entityId, entitySeries));
    }

    private Mono<AbstractCondition> getConditionInternal(ULong entityId, EntitySeries entitySeries, boolean hasParent) {
        return this.dao.readByEntityId(entityId, entitySeries, hasParent).map(SimpleRule::toCondition);
    }

    public Mono<Integer> deleteRule(ULong ruleId, EntitySeries entitySeries, boolean hasParent) {
        return this.dao
                .readByEntityId(ruleId, entitySeries, hasParent)
                .flatMap(simpleRule -> this.delete(simpleRule.getId()));
    }

    public Flux<AbstractCondition> getConditionByComplexRule(ULong complexRuleId) {
        return this.dao.readByComplexRuleId(complexRuleId).map(SimpleRule::toCondition);
    }

    public Flux<SimpleRule> getSimpleRulesByComplexRuleId(ULong complexRuleId) {
        return this.dao.readByComplexRuleId(complexRuleId);
    }

    public Mono<Integer> deleteByComplexRuleId(ULong complexRuleId) {
        return super.deleteMultiple(this.getSimpleRulesByComplexRuleId(complexRuleId));
    }

    public Mono<SimpleRule> createForConditionWithParent(
            ULong ruleId,
            EntitySeries entitySeries,
            ProcessorAccess access,
            FilterCondition condition,
            ULong parentId,
            int order) {
        return this.createForCondition(ruleId, entitySeries, access, Boolean.TRUE, condition)
                .flatMap(cSimpleRule -> {
                    SimpleComplexRuleRelation relation = this.createRelation(parentId, cSimpleRule.getId(), order);
                    return simpleComplexRuleRelationService
                            .createInternal(access, relation)
                            .thenReturn(cSimpleRule);
                });
    }

    private SimpleComplexRuleRelation createRelation(ULong complexConditionId, ULong simpleConditionId, int order) {
        return new SimpleComplexRuleRelation()
                .setComplexConditionId(complexConditionId)
                .setSimpleConditionId(simpleConditionId)
                .setOrder(order);
    }
}
