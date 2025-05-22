package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import com.google.gson.JsonElement;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTemplateRuleService
        extends RuleService<EntityProcessorValueTemplateRulesRecord, ProductTemplateRule, ProductTemplateRuleDAO> {

    private static final String VALUE_TEMPLATE_RULE_CONFIG = "valueTemplateRuleConfig";

    private ProductTemplateService productTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
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
    protected Mono<ProductTemplateRule> createFromRequest(RuleRequest ruleRequest) {
        return FlatMapUtil.flatMapMono(
                () -> productTemplateService.checkAndUpdateIdentity(ruleRequest.getEntityId()),
                productTemplateId -> Mono.just(
                        new ProductTemplateRule().of(ruleRequest).setEntityId(productTemplateId.getULongId())));
    }

    @Override
    public Mono<ULong> getUserAssignment(
            String appCode, String clientCode, ULong entityId, ULong stageId, String tokenPrefix, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> this.getRuleWithOrder(appCode, clientCode, entityId, stageId),
                productTemplateRules ->
                        super.ruleExecutionService.executeRules(productTemplateRules, tokenPrefix, data),
                (productTemplateRules, eRule) -> super.update(eRule).map(Rule::getLastAssignedUserId));
    }
}
