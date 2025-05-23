package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.rule.RuleController;
import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.service.ProductStageRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/rules")
public class ProductStageRuleController
        extends RuleController<
                EntityProcessorProductRulesRecord, ProductStageRule, ProductStageRuleDAO, ProductStageRuleService> {}
