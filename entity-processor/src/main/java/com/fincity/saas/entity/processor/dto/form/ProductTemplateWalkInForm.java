package com.fincity.saas.entity.processor.dto.form;

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
public class ProductTemplateWalkInForm extends BaseWalkInFormDto<ProductTemplateWalkInForm> {

    @Serial
    private static final long serialVersionUID = 1667873650332251053L;

    private ULong productTemplateId;

    public ProductTemplateWalkInForm() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    public ProductTemplateWalkInForm(ProductTemplateWalkInForm productTemplateWalkInForm) {
        super(productTemplateWalkInForm);
        this.productTemplateId = productTemplateWalkInForm.productTemplateId;
    }

    @Override
    public ULong getProductId() {
        return this.getProductTemplateId();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE_WALK_IN_FORMS;
    }
}
