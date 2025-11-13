package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.rule.BaseRuleController;
import com.fincity.saas.entity.processor.dao.product.ProductTicketCRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.product.ProductTicketCRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/tickets/c/rules")
public class ProductTicketCRuleController
        extends BaseRuleController<
                EntityProcessorProductTicketCRulesRecord,
                ProductTicketCRuleDto,
                ProductTicketCRuleDAO,
                EntityProcessorTicketCUserDistributionsRecord,
                TicketCUserDistribution,
                TicketCUserDistributionDAO,
                ProductTicketCRuleService> {}
