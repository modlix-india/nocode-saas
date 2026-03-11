package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.product.ProductTicketExRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.service.product.ProductTicketExRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/tickets/ex/rules")
public class ProductTicketExRuleController
        extends BaseUpdatableController<
                EntityProcessorProductTicketExRulesRecord,
                ProductTicketExRule,
                ProductTicketExRuleDAO,
                ProductTicketExRuleService> {}
