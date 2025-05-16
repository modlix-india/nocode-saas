package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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
public class ValueTemplateRule extends RuleConfig<ValueTemplateRule> {

    @Serial
    private static final long serialVersionUID = 5282289027862256173L;

    private ULong valueTemplateId;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE;
    }

    @Override
    public ULong getEntityId() {
        return this.getValueTemplateId();
    }

    @Override
    public ValueTemplateRule setEntityId(ULong entityId) {
        return this.setValueTemplateId(entityId);
    }
}
