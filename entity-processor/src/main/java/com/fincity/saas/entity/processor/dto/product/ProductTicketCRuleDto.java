package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
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
public class ProductTicketCRuleDto extends BaseRuleDto<ProductTicketCRuleDto> {

    private ULong productId;
    private boolean overrideTemplate = false;

    @Override
    public ULong getEntityId() {
        return this.getProductId();
    }

    @Override
    public ProductTicketCRuleDto setEntityId(ULong entityId) {
        return this.setProductId(entityId);
    }
}
