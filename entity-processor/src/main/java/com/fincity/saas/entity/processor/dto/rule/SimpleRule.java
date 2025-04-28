package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.ComparisonOperator;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class SimpleRule extends BaseRule<SimpleRule> {

    @Serial
    private static final long serialVersionUID = 1248302700338268L;

    private ULong ruleId;
    private String field;
    private ComparisonOperator comparisonOperator = ComparisonOperator.EQUALS;
    private Object value;
    private Object toValue;
    private boolean isValueField = false;
    private boolean isToValueField = false;
    private ComparisonOperator matchOperator = ComparisonOperator.EQUALS;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_CONDITION;
    }
}
