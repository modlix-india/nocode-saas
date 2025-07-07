package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.LogicalOperator;
import java.io.Serial;
import java.util.List;
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
    private boolean hasComplexChild;
    private boolean hasSimpleChild;

    public ComplexRule() {
        super();
    }

    public static ComplexRule fromCondition(ULong ruleId, EntitySeries entitySeries, ComplexCondition condition) {
        ComplexRule complexRule = new ComplexRule()
                .setNegate(condition.isNegate())
                .setLogicalOperator(
                        condition.getOperator() == ComplexConditionOperator.AND
                                ? LogicalOperator.AND
                                : LogicalOperator.OR);

        if (entitySeries.equals(EntitySeries.PRODUCT_STAGE_RULE)) complexRule.setProductStageRuleId(ruleId);
        else complexRule.setProductTemplateRuleId(ruleId);
        return complexRule.setName();
    }

    public static AbstractCondition toCondition(ComplexRule rule, List<AbstractCondition> conditions) {
        return new ComplexCondition()
                .setOperator(
                        rule.getLogicalOperator() == LogicalOperator.AND
                                ? ComplexConditionOperator.AND
                                : ComplexConditionOperator.OR)
                .setConditions(conditions)
                .setNegate(rule.isNegate());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.COMPLEX_RULE;
    }
}
