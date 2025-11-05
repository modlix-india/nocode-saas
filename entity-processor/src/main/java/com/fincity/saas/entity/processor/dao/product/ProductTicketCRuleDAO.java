package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES;

import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTicketCRuleDAO
        extends BaseRuleDAO<EntityProcessorProductTicketCRulesRecord, ProductTicketCRuleDto> {

    protected ProductTicketCRuleDAO() {
        super(
                ProductTicketCRuleDto.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES.ID,
                ProductTicketCRuleDto.Fields.productId);
    }
}
