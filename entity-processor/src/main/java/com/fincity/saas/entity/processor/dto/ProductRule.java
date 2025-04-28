package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.rule.base.EntityRule;
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
public class ProductRule extends EntityRule<ProductRule> {

    @Serial
    private static final long serialVersionUID = 7795039734216251997L;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE;
    }
}
