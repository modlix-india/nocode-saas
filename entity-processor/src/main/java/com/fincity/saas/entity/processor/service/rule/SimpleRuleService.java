package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.SimpleRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.base.IConditionRuleService;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
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
    protected Mono<SimpleRule> updatableEntity(SimpleRule entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            e.setField(entity.getField());
            e.setComparisonOperator(entity.getComparisonOperator());
            e.setValue(entity.getValue());
            e.setToValue(entity.getToValue());
            e.setValueField(entity.isValueField());
            e.setToValueField(entity.isToValueField());
            e.setMatchOperator(entity.getMatchOperator());
            return Mono.just(e);
        });
    }

    @Override
    public Mono<SimpleRule> createForCondition(Rule rule, FilterCondition condition) {
        SimpleRule simpleRule = SimpleRule.of(rule.getId(), condition);
        return super.create(simpleRule);
    }

    public Mono<SimpleRule> createForConditionWithParent(
            Rule rule, FilterCondition condition, ULong parentId, int order) {
        return this.createForCondition(rule, condition).flatMap(cSimpleRule -> {
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
