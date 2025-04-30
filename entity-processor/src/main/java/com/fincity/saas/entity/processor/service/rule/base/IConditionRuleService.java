package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public interface IConditionRuleService<
        C extends AbstractCondition, D extends BaseRule<D>, T extends IConditionRuleService<C, D, T>> {

    Mono<D> createForCondition(Rule rule, C condition);

    Mono<AbstractCondition> getCondition(ULong ruleId);
}
