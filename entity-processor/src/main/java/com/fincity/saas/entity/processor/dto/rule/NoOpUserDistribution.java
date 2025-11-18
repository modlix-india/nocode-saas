package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
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
public class NoOpUserDistribution extends BaseUserDistributionDto<NoOpUserDistribution> {

    @Serial
    private static final long serialVersionUID = 8575725400335506572L;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.XXX;
    }
}
