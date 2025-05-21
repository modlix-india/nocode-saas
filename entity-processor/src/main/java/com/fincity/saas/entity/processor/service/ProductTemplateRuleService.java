package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
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
    protected Mono<ProductTemplateRule> createFromRequest(RuleRequest ruleRequest) {
        return FlatMapUtil.flatMapMono(
                () -> productTemplateService.checkAndUpdateIdentity(ruleRequest.getEntityId()),
                productTemplateId -> Mono.just(
                        new ProductTemplateRule().of(ruleRequest).setEntityId(productTemplateId.getULongId())));
    }

    @Override
    protected String getCacheName() {
        return VALUE_TEMPLATE_RULE_CONFIG;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.VALUE_TEMPLATE_RULE;
    }

    //    @Override
    //    protected Mono<ULong> getEntityId(String appCode, String clientCode, Identity valueTemplateId) {
    //        return productTemplateService.readWithAccess(valueTemplateId).map(ProductTemplate::getId);
    //    }
    //
    //    @Override
    //    protected Mono<ULong> getValueTemplateId(String appCode, String clientCode, Identity entityId) {
    //        return productTemplateService.readIdentityInternal(entityId).map(ProductTemplate::getId);
    //    }
    //
    //    @Override
    //    protected Mono<ProductTemplateRule> createNewInstance() {
    //        return Mono.just(new ProductTemplateRule());
    //    }
    //
    //    @Override
    //    protected Mono<ULong> getUserAssignment(
    //            String appCode,
    //            String clientCode,
    //            ULong valueTemplateId,
    //            Platform platform,
    //            String tokenPrefix,
    //            JsonElement data) {
    //        return FlatMapUtil.flatMapMono(
    //                () -> this.read(appCode, clientCode, valueTemplateId, platform),
    //                productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, data));
    //    }
}
