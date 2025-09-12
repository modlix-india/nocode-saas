package com.modlix.saas.commons2.model.condition;

import java.io.Serial;
import java.util.List;

import com.modlix.saas.commons2.util.StringUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class FilterCondition extends AbstractCondition {

    @Serial
    private static final long serialVersionUID = -4542270694019365457L;

    private String field;
    private FilterConditionOperator operator = FilterConditionOperator.EQUALS;
    private Object value; // NOSONAR
    private Object toValue; // NOSONAR
    private List<?> multiValue; // NOSONAR
    private boolean isValueField = false;
    private boolean isToValueField = false;
    private FilterConditionOperator matchOperator = FilterConditionOperator.EQUALS;

    public static FilterCondition make(String field, Object value) {
        return new FilterCondition().setField(field).setValue(value);
    }

    public static FilterCondition of(String field, Object value, FilterConditionOperator operator) {
        return new FilterCondition().setField(field).setValue(value).setOperator(operator);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<FilterCondition> findConditionWithField(String fieldName) {

        if (StringUtil.safeEquals(field, fieldName))
            return List.of(this);

        return List.of();
    }

    @Override
    public AbstractCondition removeConditionWithField(String fieldName) {

        if (StringUtil.safeEquals(field, fieldName))
            return null;

        return this;
    }
}
