package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.rule.RuleConfigController;
import com.fincity.saas.entity.processor.dao.ValueTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRuleRequest;
import com.fincity.saas.entity.processor.service.ValueTemplateRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/values/templates/rules")
public class ValueTemplateRuleController
        extends RuleConfigController<
                ValueTemplateRuleRequest,
                EntityProcessorValueTemplateRulesRecord,
                ValueTemplateRule,
                ValueTemplateRuleDAO,
                ValueTemplateRuleService> {}
