package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.rule.Rule;
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
public class ProductStageRule extends Rule<ProductStageRule> {

    @Serial
    private static final long serialVersionUID = 3634716140733876197L;

    private ULong productId;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_STAGE_RULE;
    }

    @Override
    public ULong getEntityId() {
        return this.getProductId();
    }

    @Override
    public ProductStageRule setEntityId(ULong entityId) {
        return this.setProductId(entityId);
    }
}
