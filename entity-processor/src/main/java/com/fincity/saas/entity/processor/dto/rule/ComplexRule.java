package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.LogicalOperator;
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
public class ComplexRule extends BaseRule<ComplexRule> {

    @Serial
    private static final long serialVersionUID = 7723717368952966824L;

    private ULong parentConditionId;
    private LogicalOperator logicalOperator;

    public static ComplexRule of(ULong ruleId, ComplexCondition condition) {
        ComplexRule complexRule = new ComplexRule()
                .setRuleId(ruleId)
                .setNegate(condition.isNegate())
                .setLogicalOperator(
                        condition.getOperator() == ComplexConditionOperator.AND
                                ? LogicalOperator.AND
                                : LogicalOperator.OR);

        return complexRule.setName();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.COMPLEX_CONDITION;
    }
}
