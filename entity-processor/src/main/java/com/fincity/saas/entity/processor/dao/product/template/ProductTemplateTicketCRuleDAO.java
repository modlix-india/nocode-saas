package com.fincity.saas.entity.processor.dao.product.template;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_C_RULES;

import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.template.ProductTemplateTicketCRuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateTicketCRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateTicketCRuleDAO
        extends BaseRuleDAO<EntityProcessorProductTemplateTicketCRulesRecord, ProductTemplateTicketCRuleDto> {

    protected ProductTemplateTicketCRuleDAO() {
        super(
                ProductTemplateTicketCRuleDto.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_C_RULES,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_TICKET_C_RULES.ID,
                ProductTemplateTicketCRuleDto.Fields.productTemplateId);
    }
}
