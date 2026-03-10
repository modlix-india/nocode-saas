package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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
public class ProductTicketExRule extends BaseUpdatableDto<ProductTicketExRule> {

    private ULong productId;
    private ULong productTemplateId;
    private String source;
    private Integer expiryDays;

    public ProductTicketExRule() {
        super();
    }

    public ProductTicketExRule(ProductTicketExRule rule) {
        super(rule);
        this.productId = rule.getProductId();
        this.productTemplateId = rule.getProductTemplateId();
        this.source = rule.getSource();
        this.expiryDays = rule.getExpiryDays();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_EX_RULES;
    }
}
