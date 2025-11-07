package com.fincity.saas.entity.processor.dto.product;

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
public class ProductTicketCRuleDto extends BaseRuleDto<ProductTicketCRuleDto> {

    @Serial
    private static final long serialVersionUID = 7767234780578180385L;

    private ULong stageId;

    public ProductTicketCRuleDto() {
        super();
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
    }

    public ProductTicketCRuleDto(ProductTicketCRuleDto productTicketCRuleDto) {
        super(productTicketCRuleDto);
        this.stageId = productTicketCRuleDto.stageId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES;
    }
}
