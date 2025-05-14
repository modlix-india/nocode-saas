package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.ValueTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.dto.ValueTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRuleRequest;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ValueTemplateRuleService
        extends RuleConfigService<
                ValueTemplateRuleRequest,
                EntityProcessorValueTemplateRulesRecord,
                ValueTemplateRule,
                ValueTemplateRuleDAO> {

    private static final String VALUE_TEMPLATE_RULE_CONFIG = "valueTemplateRuleConfig";

    private ValueTemplateService valueTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ValueTemplateService valueTemplateService) {
        this.valueTemplateService = valueTemplateService;
    }

    @Override
    protected String getCacheName() {
        return VALUE_TEMPLATE_RULE_CONFIG;
    }

    @Override
    protected Mono<ULong> getEntityId(String appCode, String clientCode, ULong userId, Identity valueTemplateId) {
        return valueTemplateService.readWithAccess(valueTemplateId).map(ValueTemplate::getId);
    }

    @Override
    protected Mono<ValueTemplateRule> createNewInstance() {
        return Mono.just(new ValueTemplateRule());
    }
}
