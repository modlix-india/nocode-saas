package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
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
public class Product extends BaseProcessorDto<Product> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private ULong productTemplateId;

    public Product() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    public Product(Product product) {
        super(product);
        this.productTemplateId = product.productTemplateId;
    }

    public static Product of(ProductRequest productRequest) {
        return new Product().setName(productRequest.getName()).setDescription(productRequest.getDescription());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }
}
