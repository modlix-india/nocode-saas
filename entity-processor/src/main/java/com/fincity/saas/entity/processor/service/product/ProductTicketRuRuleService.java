package com.fincity.saas.entity.processor.service.product;

import com.fincity.saas.entity.processor.dao.product.ProductTicketRuRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRuUserDistributionService;
import org.springframework.stereotype.Service;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord,
                ProductTicketRuRuleDto,
                ProductTicketRuRuleDAO,
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRuUserDistribution,
                TicketRuUserDistributionDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";

    protected ProductTicketRuRuleService(TicketRuUserDistributionService ticketRUUserDistributionService) {
        super(ticketRUUserDistributionService);
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }
}
