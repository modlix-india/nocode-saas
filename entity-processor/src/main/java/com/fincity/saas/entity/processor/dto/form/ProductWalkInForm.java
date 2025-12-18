package com.fincity.saas.entity.processor.dto.form;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
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
@IgnoreGeneration
public class ProductWalkInForm extends BaseWalkInFormDto<ProductWalkInForm> {

    @Serial
    private static final long serialVersionUID = -6827340490522807962L;

    private ULong productId;

    public ProductWalkInForm() {
        super();
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
    }

    public ProductWalkInForm(ProductWalkInForm productWalkInForm) {
        super(productWalkInForm);
        this.productId = productWalkInForm.productId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_WALK_IN_FORMS;
    }
}
