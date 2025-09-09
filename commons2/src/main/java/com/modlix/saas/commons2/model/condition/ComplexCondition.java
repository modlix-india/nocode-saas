package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.util.List;

import com.modlix.saas.commons2.util.StringUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ComplexCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = -2971120422063853598L;

    private ComplexConditionOperator operator;
    private List<AbstractCondition> conditions;

    public static ComplexCondition and(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.AND);
    }

    public static ComplexCondition or(AbstractCondition... conditions) {
        return new ComplexCondition().setConditions(List.of(conditions)).setOperator(ComplexConditionOperator.OR);
    }

    @Override
    public boolean isEmpty() {

        return conditions == null || conditions.isEmpty();
    }

    @Override
    public List<FilterCondition> findConditionWithField(String fieldName) {

        if (StringUtil.safeIsBlank(fieldName))
            return List.of();

        return this.conditions.stream()
                .flatMap(c -> c.findConditionWithField(fieldName).stream())
                .toList();
    }

    @Override
    public AbstractCondition removeConditionWithField(String fieldName) {

        if (StringUtil.safeIsBlank(fieldName))
            return null;

        if (conditions == null || conditions.isEmpty())
            return this;

        List<AbstractCondition> updatedCond = this.conditions.stream()
                .map(cond -> cond.removeConditionWithField(fieldName))
                .filter(cond -> cond != null)
                .toList();

        if (updatedCond.isEmpty())
            return null;

        return new ComplexCondition()
                .setOperator(this.operator)
                .setConditions(updatedCond)
                .setNegate(this.isNegate());
    }
}
