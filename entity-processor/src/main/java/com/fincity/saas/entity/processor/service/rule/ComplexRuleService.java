package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.ComplexRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.base.IConditionRuleService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ComplexRuleService extends BaseRuleService<EntityProcessorComplexRulesRecord, ComplexRule, ComplexRuleDAO>
        implements IConditionRuleService<ComplexCondition, ComplexRule, ComplexRuleService> {

    private static final String COMPLEX_RULE = "complexRule";

    private SimpleRuleService simpleRuleService;

    @Lazy
    @Autowired
    private void setSimpleRuleService(SimpleRuleService simpleRuleService) {
        this.simpleRuleService = simpleRuleService;
    }

    @Override
    protected String getCacheName() {
        return COMPLEX_RULE;
    }

    @Override
    protected Mono<ComplexRule> updatableEntity(ComplexRule complexRule) {
        return super.updatableEntity(complexRule).flatMap(existing -> {
            existing.setParentConditionId(complexRule.getParentConditionId());
            existing.setLogicalOperator(complexRule.getLogicalOperator());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<ComplexRule> createForCondition(Rule rule, ComplexCondition condition) {
        return this.createComplexRuleInternal(rule, condition, null);
    }

    @Override
    public Mono<Integer> deleteByRuleId(ULong ruleId) {
        return this.dao.deleteByRuleId(ruleId);
    }

    public Mono<ComplexRule> createForConditionWithParent(Rule rule, ComplexCondition condition, ULong parentId) {
        return this.createComplexRuleInternal(rule, condition, parentId);
    }

    private Mono<ComplexRule> createComplexRuleInternal(Rule rule, ComplexCondition condition, ULong parentId) {
        if (condition.isEmpty()) return Mono.empty();

        ComplexRule complexRule = ComplexRule.of(rule.getId(), condition);

        if (parentId != null) complexRule.setParentConditionId(parentId);

        return this.create(complexRule).flatMap(cComplexRule -> {
            List<AbstractCondition> conditions = condition.getConditions();
            if (conditions == null || conditions.isEmpty()) return Mono.just(cComplexRule);

            return this.processConditions(rule, conditions, cComplexRule.getId())
                    .then(Mono.just(cComplexRule));
        });
    }

    private Flux<Void> processConditions(Rule rule, List<AbstractCondition> conditions, ULong complexRuleId) {
        return Flux.fromIterable(conditions).index().flatMap(tuple -> {
            AbstractCondition condition = tuple.getT2();
            int index = tuple.getT1().intValue();

            if (condition instanceof FilterCondition filterCondition) {
                return processSimpleCondition(rule, filterCondition, complexRuleId, index);
            } else if (condition instanceof ComplexCondition complexCondition) {
                return processComplexCondition(rule, complexCondition, complexRuleId);
            }

            return Mono.empty();
        });
    }

    private Mono<Void> processComplexCondition(Rule rule, ComplexCondition condition, ULong complexRuleId) {
        return this.createForConditionWithParent(rule, condition, complexRuleId).then(Mono.empty());
    }

    private Mono<Void> processSimpleCondition(Rule rule, FilterCondition condition, ULong complexRuleId, int index) {
        return this.simpleRuleService
                .createForConditionWithParent(rule, condition, complexRuleId, index)
                .then(Mono.empty());
    }
}
