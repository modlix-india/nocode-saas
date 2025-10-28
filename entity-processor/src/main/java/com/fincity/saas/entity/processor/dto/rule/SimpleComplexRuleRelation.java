package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
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
public class SimpleComplexRuleRelation extends BaseUpdatableDto<SimpleComplexRuleRelation> {

    @Serial
    private static final long serialVersionUID = 9035530855293927614L;

    private ULong complexConditionId;
    private ULong simpleConditionId;
    private Integer order;

    public SimpleComplexRuleRelation() {
        super();
    }

    public SimpleComplexRuleRelation(SimpleComplexRuleRelation simpleComplexRuleRelation) {
        super(simpleComplexRuleRelation);
        this.complexConditionId = simpleComplexRuleRelation.complexConditionId;
        this.simpleConditionId = simpleComplexRuleRelation.simpleConditionId;
        this.order = simpleComplexRuleRelation.order;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_COMPLEX_CONDITION_RELATION;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.complexConditionId,
                Schema.ofLong(Fields.complexConditionId).setMinimum(1));
        props.put(
                Fields.simpleConditionId,
                Schema.ofLong(Fields.simpleConditionId).setMinimum(1));
        props.put(Fields.order, Schema.ofInteger(Fields.order).setMinimum(0));

        schema.setProperties(props);
        return schema;
    }
}
