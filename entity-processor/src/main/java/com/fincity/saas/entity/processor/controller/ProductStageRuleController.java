package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.rule.RuleController;
import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductStageRulesRecord;
import com.fincity.saas.entity.processor.service.ProductStageRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/rules")
public class ProductStageRuleController
        extends RuleController<
                EntityProcessorProductStageRulesRecord,
                ProductStageRule,
                ProductStageRuleDAO,
                ProductStageRuleService> {}
