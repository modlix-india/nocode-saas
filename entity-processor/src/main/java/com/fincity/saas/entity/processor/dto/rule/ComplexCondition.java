package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.LogicalOperator;

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
public class ComplexCondition extends BaseRule<ComplexCondition> {

    @Serial
    private static final long serialVersionUID = 7723717368952966824L;

    private ULong ruleId;
    private LogicalOperator logicalOperator;
    private boolean negate = false;
    private ULong parentConditionId;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.COMPLEX_CONDITION;
    }
}
