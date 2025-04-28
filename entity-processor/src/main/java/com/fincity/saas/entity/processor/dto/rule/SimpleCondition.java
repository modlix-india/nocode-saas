package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.SimpleConditionOperator;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class SimpleCondition extends BaseRule<SimpleCondition>{

    @Serial
    private static final long serialVersionUID = 1248302700338268L;

    private ULong ruleId;
    private String field;
    private SimpleConditionOperator operator = SimpleConditionOperator.EQUALS;
    private Object value;
    private Object toValue;
    private boolean isValueField = false;
    private boolean isToValueField = false;
    private boolean negate = false;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_CONDITION;
    }
}
