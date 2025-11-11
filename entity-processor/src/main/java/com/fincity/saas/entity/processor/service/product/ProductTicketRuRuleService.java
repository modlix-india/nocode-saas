package com.fincity.saas.entity.processor.service.product;

import com.fincity.saas.entity.processor.dao.product.ProductTicketRURuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRURuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRUUserDistributionService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord,
                TicketRUUserDistribution,
                ProductTicketRURuleDto,
                ProductTicketRURuleDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";

    @Getter
    private TicketRUUserDistributionService userDistributionService;

    @Autowired
    private void setUserDistributionService(TicketRUUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }
}
