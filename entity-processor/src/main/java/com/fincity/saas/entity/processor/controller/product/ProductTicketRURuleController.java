package com.fincity.saas.entity.processor.controller.product;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.entity.processor.controller.rule.BaseRuleController;
import com.fincity.saas.entity.processor.dao.product.ProductTicketRURuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRURuleDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;

@RestController
@RequestMapping("api/entity/processor/products/tickets/ru/rules")
public class ProductTicketRURuleController
        extends BaseRuleController<
                EntityProcessorProductTicketRuRulesRecord,
                TicketRUUserDistribution,
                ProductTicketRURuleDto,
                ProductTicketRURuleDAO,
                ProductTicketRuRuleService> {}
