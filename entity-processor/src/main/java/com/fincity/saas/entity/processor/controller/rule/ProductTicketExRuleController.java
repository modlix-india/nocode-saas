package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.ProductTicketExRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.service.rule.ProductTicketExRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tickets/expiration/rules")
public class ProductTicketExRuleController
        extends BaseUpdatableController<
                EntityProcessorProductTicketExRulesRecord,
                ProductTicketExRule,
                ProductTicketExRuleDAO,
                ProductTicketExRuleService> {}
