package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.rule.RuleConfigController;
import com.fincity.saas.entity.processor.dao.ProductRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.request.ProductRuleRequest;
import com.fincity.saas.entity.processor.service.ProductRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity-processor/products/rules")
public class ProductRuleController
        extends RuleConfigController<
                ProductRuleRequest,
                EntityProcessorProductRulesRecord,
                ProductRule,
                ProductRuleDAO,
                ProductRuleService> {}
