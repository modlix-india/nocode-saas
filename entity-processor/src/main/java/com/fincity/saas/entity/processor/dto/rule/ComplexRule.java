package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.rule.LogicalOperator;
import com.google.gson.JsonPrimitive;
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

    public ComplexRule(ComplexRule complexRule) {
        super(complexRule);
        this.parentConditionId = complexRule.parentConditionId;
        this.logicalOperator = complexRule.logicalOperator;
        this.hasComplexChild = complexRule.hasComplexChild;
        this.hasSimpleChild = complexRule.hasSimpleChild;
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

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.parentConditionId,
                Schema.ofLong(Fields.parentConditionId).setMinimum(1));
        props.put(
                Fields.logicalOperator,
                Schema.ofString(Fields.logicalOperator).setEnums(EnumSchemaUtil.getSchemaEnums(LogicalOperator.class)));
        props.put(
                Fields.hasComplexChild,
                Schema.ofBoolean(Fields.hasComplexChild).setDefaultValue(new JsonPrimitive(false)));
        props.put(
                Fields.hasSimpleChild,
                Schema.ofBoolean(Fields.hasSimpleChild).setDefaultValue(new JsonPrimitive(false)));

        schema.setProperties(props);
        return schema;
    }
}
