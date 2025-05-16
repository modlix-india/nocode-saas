package com.fincity.saas.entity.processor.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ValueTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.dto.ValueTemplateRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRuleRequest;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
import com.google.gson.JsonElement;

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
    public EntitySeries getEntitySeries() {
        return EntitySeries.VALUE_TEMPLATE_RULE;
    }

    @Override
    protected Mono<ULong> getEntityId(String appCode, String clientCode, Identity valueTemplateId) {
        return valueTemplateService.readWithAccess(valueTemplateId).map(ValueTemplate::getId);
    }

    @Override
    protected Mono<ULong> getValueTemplateId(String appCode, String clientCode, Identity entityId) {
        return valueTemplateService.readIdentityInternal(entityId).map(ValueTemplate::getId);
    }

    @Override
    protected Mono<ValueTemplateRule> createNewInstance() {
        return Mono.just(new ValueTemplateRule());
    }

    @Override
    protected Mono<ULong> getUserAssignment(
            String appCode, String clientCode, ULong valueTemplateId, Platform platform, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(appCode, clientCode, valueTemplateId, platform),
                productRule -> super.ruleExecutionService.executeRules(productRule, data));
    }
}
