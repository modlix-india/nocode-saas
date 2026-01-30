package com.fincity.saas.commons.model.condition;

import java.io.Serial;

import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class HavingCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = 9016071634336786144L;

    private AggregateFunction aggregateFunction = AggregateFunction.MAX;
    private FilterCondition condition;

    public HavingCondition() {
        super();
    }

    public HavingCondition(HavingCondition condition) {
        super();
        this.aggregateFunction = condition.getAggregateFunction();
        this.condition = CloneUtil.cloneObject(condition.getCondition());
        this.setNegate(condition.isNegate());
    }

    public static HavingCondition make(String field, AggregateFunction function, Object value) {
        return new HavingCondition().setAggregateFunction(function).setCondition(FilterCondition.make(field, value));
    }

    public static HavingCondition of(
            String field, AggregateFunction function, Object value, FilterConditionOperator operator) {
        FilterCondition fc = FilterCondition.of(field, value, operator);
        return new HavingCondition().setAggregateFunction(function).setCondition(fc);
    }

    @Override
    public boolean isEmpty() {
        return this.aggregateFunction == null || this.condition == null || this.condition.isEmpty();
    }

    @Override
    public Flux<FilterCondition> findConditionWithField(String fieldName) {

        if (this.condition == null || StringUtil.safeIsBlank(fieldName)) return Flux.empty();
        return this.condition.findConditionWithField(fieldName);
    }

    @Override
    public Flux<FilterCondition> findConditionWithPrefix(String prefix) {

        if (this.condition == null || StringUtil.safeIsBlank(prefix)) return Flux.empty();
        return this.condition.findConditionWithPrefix(prefix);
    }

    @Override
    public Flux<FilterCondition> findAndTrimPrefix(String prefix) {

        if (this.condition == null || StringUtil.safeIsBlank(prefix)) return Flux.empty();
        return this.condition.findAndTrimPrefix(prefix);
    }

    @Override
    public Flux<FilterCondition> findAndCreatePrefix(String prefix) {

        if (this.condition == null || StringUtil.safeIsBlank(prefix)) return Flux.empty();
        return this.condition.findAndCreatePrefix(prefix);
    }

    @Override
    public Mono<AbstractCondition> removeConditionWithField(String fieldName) {

        if (this.condition == null || StringUtil.safeIsBlank(fieldName)) return Mono.just(this);

        return this.condition
                .removeConditionWithField(fieldName)
                .map(removed -> (AbstractCondition) new HavingCondition(this).setCondition((FilterCondition) removed))
                .switchIfEmpty(Mono.empty());
    }
}
