package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.ProductTemplateType;
import com.fincity.saas.entity.processor.model.request.ProductTemplateRequest;
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
public class ProductTemplate extends BaseUpdatableDto<ProductTemplate> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 2361640922389483322L;

    private ProductTemplateType productTemplateType;

    public static ProductTemplate of(ProductTemplateRequest productTemplateRequest) {
        return new ProductTemplate()
                .setName(productTemplateRequest.getName())
                .setDescription(productTemplateRequest.getDescription())
                .setProductTemplateType(productTemplateRequest.getProductTemplateType());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE;
    }
}
