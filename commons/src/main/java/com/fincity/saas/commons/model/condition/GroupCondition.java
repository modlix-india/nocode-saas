package com.fincity.saas.commons.model.condition;

import java.io.Serial;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class GroupCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = 7278727849167123544L;

    private ComplexConditionOperator operator = ComplexConditionOperator.AND;
    private List<String> fields = List.of();
    private List<HavingCondition> havingConditions = List.of();

    public static GroupCondition of(List<String> fields, List<HavingCondition> havingConditions) {
        return of(ComplexConditionOperator.AND, fields, havingConditions);
    }

    public static GroupCondition of(List<String> fields, HavingCondition havingCondition) {
        return of(ComplexConditionOperator.AND, fields, havingCondition != null ? List.of(havingCondition) : List.of());
    }

    public static GroupCondition of(
            ComplexConditionOperator operator, List<String> fields, List<HavingCondition> havingConditions) {
        return new GroupCondition()
                .setOperator(operator != null ? operator : ComplexConditionOperator.AND)
                .setFields(fields != null ? List.copyOf(fields) : List.of())
                .setHavingConditions(havingConditions != null ? List.copyOf(havingConditions) : List.of());
    }

    public static GroupCondition of(
            ComplexConditionOperator operator, List<String> fields, HavingCondition havingCondition) {
        return of(operator, fields, havingCondition != null ? List.of(havingCondition) : List.of());
    }

    @Override
    public boolean isEmpty() {
        return (fields == null || fields.isEmpty()) && (havingConditions == null || havingConditions.isEmpty());
    }

    @Override
    public Flux<FilterCondition> findConditionWithField(String fieldName) {
        return this.mapHavingConditions(fieldName, hc -> hc.getCondition().findConditionWithField(fieldName));
    }

    @Override
    public Flux<FilterCondition> findConditionWithPrefix(String prefix) {
        return this.mapHavingConditions(prefix, hc -> hc.getCondition().findConditionWithPrefix(prefix));
    }

    @Override
    public Flux<FilterCondition> findAndTrimPrefix(String prefix) {
        return this.mapHavingConditions(prefix, hc -> hc.getCondition().findAndTrimPrefix(prefix));
    }

    @Override
    public Flux<FilterCondition> findAndCreatePrefix(String prefix) {
        return this.mapHavingConditions(prefix, hc -> hc.getCondition().findAndCreatePrefix(prefix));
    }

    private Flux<FilterCondition> mapHavingConditions(
            String param, Function<HavingCondition, Flux<FilterCondition>> mapper) {
        if (StringUtil.safeIsBlank(param) || havingConditions == null || havingConditions.isEmpty())
            return Flux.empty();

        return Flux.fromIterable(havingConditions)
                .filter(hc -> hc != null && hc.getCondition() != null)
                .flatMap(mapper);
    }

    @Override
    public Mono<AbstractCondition> removeConditionWithField(String fieldName) {
        if (StringUtil.safeIsBlank(fieldName)) return Mono.empty();
        if (havingConditions == null || havingConditions.isEmpty()) return Mono.just(this);

        return Flux.fromIterable(havingConditions)
                .flatMap(hc -> hc.removeConditionWithField(fieldName))
                .ofType(HavingCondition.class)
                .filter(hc -> !hc.isEmpty())
                .collectList()
                .filter(updated -> !updated.isEmpty())
                .map(updated -> new GroupCondition()
                        .setOperator(this.operator)
                        .setFields(this.fields)
                        .setHavingConditions(updated)
                        .setNegate(this.isNegate()));
    }

    public Flux<HavingCondition> getHavingConditions() {
        if (havingConditions == null || havingConditions.isEmpty()) return Flux.empty();
        return Flux.fromIterable(havingConditions).filter(hc -> hc != null && !hc.isEmpty());
    }

    public List<String> getFields() {
        return fields != null ? fields : List.of();
    }

}
