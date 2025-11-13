package com.fincity.saas.entity.processor.service.product;

import com.fincity.saas.entity.processor.dao.product.ProductTicketRURuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketRUUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRURuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRUUserDistributionService;
import org.springframework.stereotype.Service;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord,
                ProductTicketRURuleDto,
                ProductTicketRURuleDAO,
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRUUserDistribution,
                TicketRUUserDistributionDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";

    protected ProductTicketRuRuleService(TicketRUUserDistributionService ticketRUUserDistributionService) {
        super(ticketRUUserDistributionService);
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }
}
