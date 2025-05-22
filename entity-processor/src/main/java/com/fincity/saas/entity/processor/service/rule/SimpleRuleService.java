package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.SimpleRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
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
            existing.setToValue(entity.getToValue());
            existing.setValueField(entity.isValueField());
            existing.setToValueField(entity.isToValueField());
            existing.setMatchOperator(entity.getMatchOperator());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<SimpleRule> createForCondition(ULong ruleId, EntitySeries entitySeries, FilterCondition condition) {
        SimpleRule simpleRule = SimpleRule.fromCondition(ruleId, entitySeries, condition);
        return super.create(simpleRule);
    }

    @Override
    public Mono<AbstractCondition> getCondition(ULong ruleId) {
        return this.read(ruleId).map(SimpleRule::toCondition);
    }

    public Flux<AbstractCondition> getConditionByComplexRule(ULong complexRuleId) {
        return this.dao.readByComplexRuleId(complexRuleId).map(SimpleRule::toCondition);
    }

    public Mono<SimpleRule> createForConditionWithParent(
            ULong ruleId, EntitySeries entitySeries, FilterCondition condition, ULong parentId, int order) {
        return this.createForCondition(ruleId, entitySeries, condition).flatMap(cSimpleRule -> {
            SimpleComplexRuleRelation relation = this.createRelation(parentId, cSimpleRule.getId(), order);
            return simpleComplexRuleRelationService.create(relation).then(Mono.empty());
        });
    }

    private SimpleComplexRuleRelation createRelation(ULong complexConditionId, ULong simpleConditionId, int order) {
        return new SimpleComplexRuleRelation()
                .setComplexConditionId(complexConditionId)
                .setSimpleConditionId(simpleConditionId)
                .setOrder(order);
    }
}
