package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.rule.RuleController;
import com.fincity.saas.entity.processor.dao.ProductTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.service.ProductTemplateRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/values/templates/rules")
public class ProductTemplateRuleController
        extends RuleController<
                EntityProcessorValueTemplateRulesRecord,
                ProductTemplateRule,
                ProductTemplateRuleDAO,
                ProductTemplateRuleService> {}
