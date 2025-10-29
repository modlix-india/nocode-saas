package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.ComparisonOperator;
import com.fincity.saas.entity.processor.model.common.ValueContainer;
import java.io.Serial;
import java.util.List;
import java.util.Map;
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

    private boolean hasParent;
    private String field;
    private ComparisonOperator comparisonOperator = ComparisonOperator.EQUALS;
    private ValueContainer value;
    private boolean isValueField;
    private boolean isToValueField;
    private ComparisonOperator matchOperator = ComparisonOperator.EQUALS;

    public SimpleRule() {
        super();
    }

    public SimpleRule(SimpleRule simpleRule) {
        super(simpleRule);
        this.hasParent = simpleRule.hasParent;
        this.field = simpleRule.field;
        this.comparisonOperator = simpleRule.comparisonOperator;
        this.value = CloneUtil.cloneObject(simpleRule.value);
        this.isValueField = simpleRule.isValueField;
        this.isToValueField = simpleRule.isToValueField;
        this.matchOperator = simpleRule.matchOperator;
    }

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

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.hasParent, DbSchema.ofBooleanFalse(Fields.hasParent));
        props.put(Fields.field, DbSchema.ofCharNull(Fields.field, 255));
        props.put(
                Fields.comparisonOperator,
                DbSchema.ofEnum(Fields.comparisonOperator, ComparisonOperator.class, ComparisonOperator.EQUALS));
        props.put(Fields.value, ValueContainer.getSchema());
        props.put(Fields.isValueField, DbSchema.ofBooleanFalse(Fields.isValueField));
        props.put(Fields.isToValueField, DbSchema.ofBooleanFalse(Fields.isToValueField));
        props.put(
                Fields.matchOperator,
                DbSchema.ofEnum(Fields.matchOperator, ComparisonOperator.class, ComparisonOperator.EQUALS));

        schema.setProperties(props);
    }
}
