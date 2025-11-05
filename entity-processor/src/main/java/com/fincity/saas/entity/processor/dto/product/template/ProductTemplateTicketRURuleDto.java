package com.fincity.saas.entity.processor.dto.product.template;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
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
public class ProductTemplateTicketRURuleDto extends BaseRuleDto<ProductTemplateTicketRURuleDto> {

    @Serial
    private static final long serialVersionUID = 396263840947710522L;

    private ULong productTemplateId;

    public ProductTemplateTicketRURuleDto() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    public ProductTemplateTicketRURuleDto(ProductTemplateTicketRURuleDto productTemplateTicketRURule) {
        super(productTemplateTicketRURule);
        this.productTemplateId = productTemplateTicketRURule.productTemplateId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE_RULE;
    }

    @Override
    public ULong getEntityId() {
        return this.getProductTemplateId();
    }

    @Override
    public ProductTemplateTicketRURuleDto setEntityId(ULong entityId) {
        return this.setProductTemplateId(entityId);
    }
}
