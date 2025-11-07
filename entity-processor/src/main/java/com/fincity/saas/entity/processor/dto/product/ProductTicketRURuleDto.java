package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
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
public class ProductTicketRURuleDto extends BaseRuleDto<ProductTicketRURuleDto> {

    @Serial
    private static final long serialVersionUID = 3839778153632333678L;

    private boolean canEdit;

    public ProductTicketRURuleDto() {
        super();
    }

    public ProductTicketRURuleDto(ProductTicketRURuleDto productTicketRURuleDto) {
        super(productTicketRURuleDto);
        this.canEdit = productTicketRURuleDto.canEdit;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES;
    }
}
