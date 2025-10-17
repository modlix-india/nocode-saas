package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.dao.ProductTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateRulesRecord;
import com.fincity.saas.entity.processor.service.rule.ProductTemplateRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/templates/rules")
public class ProductTemplateRuleController
        extends RuleController<
                EntityProcessorProductTemplateRulesRecord,
                ProductTemplateRule,
                ProductTemplateRuleDAO,
                ProductTemplateRuleService> {}
