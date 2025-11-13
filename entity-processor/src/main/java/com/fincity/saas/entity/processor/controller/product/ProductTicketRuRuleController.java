package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.rule.BaseRuleController;
import com.fincity.saas.entity.processor.dao.product.ProductTicketRuRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/tickets/ru/rules")
public class ProductTicketRuRuleController
        extends BaseRuleController<
                EntityProcessorProductTicketRuRulesRecord,
                ProductTicketRuRuleDto,
                ProductTicketRuRuleDAO,
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRuUserDistribution,
                TicketRuUserDistributionDAO,
                ProductTicketRuRuleService> {}
