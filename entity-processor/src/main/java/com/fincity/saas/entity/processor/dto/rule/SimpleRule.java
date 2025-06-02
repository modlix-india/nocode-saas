package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.ComparisonOperator;
import com.fincity.saas.entity.processor.model.common.ValueContainer;
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
public class SimpleRule extends BaseRule<SimpleRule> {

    @Serial
    private static final long serialVersionUID = 1248302700338268L;

    private boolean hasParent = false;
    private String field;
    private ComparisonOperator comparisonOperator = ComparisonOperator.EQUALS;
    private ValueContainer value;
    private boolean isValueField = false;
    private boolean isToValueField = false;
    private ComparisonOperator matchOperator = ComparisonOperator.EQUALS;

    public static SimpleRule fromCondition(ULong ruleId, EntitySeries entitySeries, FilterCondition condition) {
        SimpleRule simpleRule = new SimpleRule()
                .setField(condition.getField())
                .setComparisonOperator(ComparisonOperator.lookup(condition.getOperator()))
                .setValue(condition.getValue(), condition.getToValue(), condition.getMultiValue())
                .setValueField(condition.isValueField())
                .setToValueField(condition.isToValueField())
                .setMatchOperator(ComparisonOperator.lookup(condition.getMatchOperator()))
                .setNegate(condition.isNegate());

        if (entitySeries.equals(EntitySeries.PRODUCT_STAGE_RULE)) simpleRule.setProductStageRuleId(ruleId);
        else simpleRule.setProductTemplateRuleId(ruleId);

        return simpleRule.setName();
    }

    public static AbstractCondition toCondition(SimpleRule rule) {
        return new FilterCondition()
                .setField(rule.getField())
                .setOperator(rule.getComparisonOperator().getConditionOperator())
                .setValue(rule.getValue().getValue())
                .setToValue(rule.getValue().getToValue())
                .setMultiValue(rule.getValue().getMultiValue())
                .setValueField(rule.isValueField())
                .setToValueField(rule.isToValueField())
                .setMatchOperator(rule.getMatchOperator().getConditionOperator())
                .setNegate(rule.isNegate());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_RULE;
    }

    public SimpleRule setValue(Object value, Object toValue, List<?> multiValue) {
        this.value = new ValueContainer().setValue(value).setToValue(toValue);
        if (multiValue != null) this.value.setMultiValue(multiValue);
        return this;
    }
}
