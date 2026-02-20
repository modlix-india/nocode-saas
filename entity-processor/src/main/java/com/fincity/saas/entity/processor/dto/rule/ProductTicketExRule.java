package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
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
public class ProductTicketExRule extends BaseUpdatableDto<ProductTicketExRule> {

    @Serial
    private static final long serialVersionUID = 3397710676428354822L;

    private ULong productId;
    private ULong productTemplateId;
    private String source;
    private Integer expiryDays;

    public ProductTicketExRule() {
        super();
    }

    public ProductTicketExRule(ProductTicketExRule rule) {
        super(rule);
        this.productId = rule.productId;
        this.productTemplateId = rule.productTemplateId;
        this.source = rule.source;
        this.expiryDays = rule.expiryDays;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_EX_RULES;
    }
}
