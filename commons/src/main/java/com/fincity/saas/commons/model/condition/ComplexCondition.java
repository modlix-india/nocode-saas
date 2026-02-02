package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private List<AbstractCondition> conditions;
    private GroupCondition groupCondition;

    public static ComplexCondition and(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.AND);
    }

    public static ComplexCondition and(List<AbstractCondition> conditions) {
        return new ComplexCondition().setConditions(List.copyOf(conditions)).setOperator(ComplexConditionOperator.AND);
    }

    public static ComplexCondition or(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.OR);
    }

    public static ComplexCondition or(List<AbstractCondition> conditions) {
        return new ComplexCondition().setConditions(List.copyOf(conditions)).setOperator(ComplexConditionOperator.OR);
    }

    @Override
    public boolean isEmpty() {
        return conditions == null || conditions.isEmpty();
    }

    @Override
    public boolean hasGroupCondition() {
        return groupCondition != null && !groupCondition.isEmpty();
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
                            .setNegate(this.isNegate()));
                });
    }

    @JsonIgnore
    @Override
    public AbstractCondition getWhereCondition() {
        if (this.conditions == null || this.conditions.isEmpty()) return null;

	    if (conditions.size() == 1) return conditions.getFirst();

        return new ComplexCondition()
                .setConditions(this.conditions)
                .setOperator(this.operator)
                .setNegate(this.isNegate());
    }
}
