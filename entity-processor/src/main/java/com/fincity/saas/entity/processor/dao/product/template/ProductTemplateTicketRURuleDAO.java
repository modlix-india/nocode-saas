package com.fincity.saas.entity.processor.dao.product.template;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_RU_RULES;

import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.template.ProductTemplateTicketRURuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateTicketRuRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateTicketRURuleDAO
        extends BaseRuleDAO<EntityProcessorProductTemplateTicketRuRulesRecord, ProductTemplateTicketRURuleDto> {

    protected ProductTemplateTicketRURuleDAO() {
        super(
                ProductTemplateTicketRURuleDto.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_RU_RULES,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_RU_RULES.ID,
                ProductTemplateTicketRURuleDto.Fields.productTemplateId);
    }
}
