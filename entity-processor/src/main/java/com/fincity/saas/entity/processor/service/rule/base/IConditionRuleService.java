package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

public interface IConditionRuleService<
        C extends AbstractCondition, D extends BaseRule<D>, T extends IConditionRuleService<C, D, T>> {

    Mono<D> createForCondition(
            ULong ruleId, EntitySeries entitySeries, Tuple3<String, String, ULong> access, C condition);

    Mono<AbstractCondition> getCondition(ULong ruleId);
}
