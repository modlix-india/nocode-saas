package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
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
public class ProductTicketRuRule extends BaseRuleDto<TicketRuUserDistribution, ProductTicketRuRule> {

    @Serial
    private static final long serialVersionUID = 3839778153632333678L;

    private boolean canEdit;
    private boolean overrideRuTemplate;

    public ProductTicketRuRule() {
        super();
    }

    public ProductTicketRuRule(ProductTicketRuRule productTicketRURule) {
        super(productTicketRURule);
        this.canEdit = productTicketRURule.canEdit;
        this.overrideRuTemplate = productTicketRURule.overrideRuTemplate;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_RU_RULE;
    }
}
