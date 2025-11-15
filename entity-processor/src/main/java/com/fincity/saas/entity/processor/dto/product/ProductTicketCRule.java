package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
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
public class ProductTicketCRule extends BaseRuleDto<TicketCUserDistribution, ProductTicketCRule> {

    @Serial
    private static final long serialVersionUID = 7767234780578180385L;

    private ULong stageId;

    public ProductTicketCRule() {
        super();
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
    }

    public ProductTicketCRule(ProductTicketCRule productTicketCRule) {
        super(productTicketCRule);
        this.stageId = productTicketCRule.stageId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES;
    }
}
