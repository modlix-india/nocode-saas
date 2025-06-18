package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
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
public class SimpleComplexRuleRelation extends BaseUpdatableDto<SimpleComplexRuleRelation> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 9035530855293927614L;

    private ULong complexConditionId;
    private ULong simpleConditionId;
    private Integer order;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_COMPLEX_CONDITION_RELATION;
    }
}
