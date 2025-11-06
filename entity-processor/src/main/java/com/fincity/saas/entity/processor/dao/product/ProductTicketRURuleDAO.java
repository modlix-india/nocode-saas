package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES;

import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRURuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTicketRURuleDAO
        extends BaseRuleDAO<EntityProcessorProductTicketRuRulesRecord, ProductTicketRURuleDto> {

    protected ProductTicketRURuleDAO() {
        super(
                ProductTicketRURuleDto.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.ID);
    }
}
