package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.modlix.saas.commons2.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class GroupCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = 8680537065493791257L;

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
    public boolean hasGroupCondition() {
        return true;
    }

    @Override
    public AbstractCondition getGroupCondition() {
        return this;
    }

    @Override
    public List<FilterCondition> findConditionWithField(String fieldName) {
        return this.mapHavingConditions(fieldName, hc -> hc.getCondition().findConditionWithField(fieldName));
    }

    private List<FilterCondition> mapHavingConditions(
            String param, Function<HavingCondition, List<FilterCondition>> mapper) {
        if (StringUtil.safeIsBlank(param) || havingConditions == null || havingConditions.isEmpty())
            return List.of();

        List<FilterCondition> result = new ArrayList<>();
        for (HavingCondition hc : havingConditions) {
            if (hc != null && hc.getCondition() != null)
                result.addAll(mapper.apply(hc));
        }
        return result;
    }

    @Override
    public AbstractCondition removeConditionWithField(String fieldName) {
        if (StringUtil.safeIsBlank(fieldName)) return this;
        if (havingConditions == null || havingConditions.isEmpty()) return this;

        List<HavingCondition> updated = new ArrayList<>();
        for (HavingCondition hc : havingConditions) {
            AbstractCondition removed = hc.removeConditionWithField(fieldName);
            if (removed instanceof HavingCondition updatedHc && !updatedHc.isEmpty())
                updated.add(updatedHc);
        }
        if (updated.isEmpty()) return null;

        return new GroupCondition()
                .setOperator(this.operator)
                .setFields(this.fields)
                .setHavingConditions(updated)
                .setNegate(this.isNegate());
    }

    public List<HavingCondition> getHavingConditions() {
        if (havingConditions == null || havingConditions.isEmpty()) return List.of();
        return havingConditions.stream()
                .filter(hc -> hc != null && !hc.isEmpty())
                .toList();
    }

    public List<String> getFields() {
        return fields != null ? fields : List.of();
    }
}
