package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.util.List;

import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ComplexCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = -2971120422063853598L;

    private ComplexConditionOperator operator;
    /** WHERE clause conditions only; operator (AND/OR) applies to these. Do not put HavingCondition here. */
    private List<AbstractCondition> conditions;
    /** HAVING clause; separate from conditions since operator does not apply to it. */
    private HavingCondition havingCondition;

    public static ComplexCondition and(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.AND);
    }

    public static ComplexCondition and(List<AbstractCondition> conditions) {
        return new ComplexCondition().setConditions(List.copyOf(conditions)).setOperator(ComplexConditionOperator.AND);
    }

    public static ComplexCondition andWithHaving(List<AbstractCondition> conditions, HavingCondition havingCondition) {
        return new ComplexCondition()
                .setConditions(List.copyOf(conditions))
                .setOperator(ComplexConditionOperator.AND)
                .setHavingCondition(havingCondition);
    }

    public static ComplexCondition or(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.OR);
    }

    public static ComplexCondition or(List<AbstractCondition> conditions) {
        return new ComplexCondition().setConditions(List.copyOf(conditions)).setOperator(ComplexConditionOperator.OR);
    }

    @Override
    public boolean isEmpty() {
        boolean conditionsEmpty = conditions == null || conditions.isEmpty();
        boolean havingEmpty = havingCondition == null || havingCondition.isEmpty();
        return conditionsEmpty && havingEmpty;
    }

    @Override
    public Flux<FilterCondition> findConditionWithField(String fieldName) {

        if (StringUtil.safeIsBlank(fieldName)) return Flux.empty();

        if (this.conditions == null || this.conditions.isEmpty()) return Flux.empty();

        return Flux.fromIterable(this.conditions).flatMap(c -> c.findConditionWithField(fieldName));
    }

    @Override
    public Flux<FilterCondition> findConditionWithPrefix(String prefix) {
        if (StringUtil.safeIsBlank(prefix)) return Flux.empty();
        if (this.conditions == null || this.conditions.isEmpty()) return Flux.empty();

        return Flux.fromIterable(this.conditions).flatMap(c -> c.findConditionWithPrefix(prefix));
    }

    @Override
    public Flux<FilterCondition> findAndTrimPrefix(String prefix) {
        if (StringUtil.safeIsBlank(prefix)) return Flux.empty();
        if (this.conditions == null || this.conditions.isEmpty()) return Flux.empty();

        return Flux.fromIterable(this.conditions).flatMap(c -> c.findAndTrimPrefix(prefix));
    }

    @Override
    public Flux<FilterCondition> findAndCreatePrefix(String prefix) {
        if (StringUtil.safeIsBlank(prefix)) return Flux.empty();
        if (this.conditions == null || this.conditions.isEmpty()) return Flux.empty();

        return Flux.fromIterable(this.conditions).flatMap(c -> c.findAndCreatePrefix(prefix));
    }

    @Override
    public Mono<AbstractCondition> removeConditionWithField(String fieldName) {

        if (StringUtil.safeIsBlank(fieldName)) return Mono.empty();

        if (conditions == null || conditions.isEmpty()) return Mono.just(this);

        return Flux.fromIterable(conditions)
                .flatMap(cond -> cond.removeConditionWithField(fieldName))
                .collectList()
                .flatMap(updatedCond -> {
                    if (updatedCond.isEmpty()) return Mono.empty();
                    return Mono.just(new ComplexCondition()
                            .setOperator(this.operator)
                            .setConditions(updatedCond)
                            .setHavingCondition(this.havingCondition)
                            .setNegate(this.isNegate()));
                });
    }

    @Override
    public Mono<HavingCondition> getHavingCondition() {
        if (this.havingCondition != null && !this.havingCondition.isEmpty()) return Mono.just(this.havingCondition);

        return Mono.empty();
    }

    @Override
    public Mono<AbstractCondition> removeHavingConditions() {
        if (this.conditions == null || this.conditions.isEmpty()) return Mono.empty();

        return Mono.just(new ComplexCondition()
                .setOperator(this.operator)
                .setConditions(this.conditions)
                .setNegate(this.isNegate()));
    }
}
