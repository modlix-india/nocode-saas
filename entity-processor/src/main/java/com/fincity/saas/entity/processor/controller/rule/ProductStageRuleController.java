package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductStageRulesRecord;
import com.fincity.saas.entity.processor.service.rule.ProductStageRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/stages/rules")
public class ProductStageRuleController
        extends RuleController<
                EntityProcessorProductStageRulesRecord,
                ProductStageRule,
                ProductStageRuleDAO,
                ProductStageRuleService> {}
