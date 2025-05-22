package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import com.google.gson.JsonElement;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductStageRuleService
        extends RuleService<EntityProcessorProductRulesRecord, ProductStageRule, ProductStageRuleDAO> {

    private static final String PRODUCT_STAGE_RULE = "productStageRule";

    private ProductService productService;
    private ProductTemplateRuleService productTemplateRuleService;

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Lazy
    @Autowired
    private void setValueTemplateRuleService(ProductTemplateRuleService productTemplateRuleService) {
        this.productTemplateRuleService = productTemplateRuleService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_STAGE_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_STAGE_RULE;
    }

    @Override
    protected Mono<ProductStageRule> createFromRequest(RuleRequest ruleRequest) {
        return FlatMapUtil.flatMapMono(
                () -> productService.checkAndUpdateIdentity(ruleRequest.getEntityId()),
                productId -> Mono.just(new ProductStageRule().of(ruleRequest).setEntityId(productId.getULongId())));
    }

    @Override
    public Mono<ULong> getUserAssignment(
            String appCode, String clientCode, ULong entityId, ULong stageId, String tokenPrefix, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getRuleWithOrder(appCode, clientCode, entityId, stageId),
                        productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, data),
                        (productRule, eRule) -> super.update(eRule).map(Rule::getLastAssignedUserId))
                .switchIfEmpty(
                        this.getUserAssignmentFromTemplate(appCode, clientCode, entityId, stageId, tokenPrefix, data));
    }

    public Mono<ULong> getUserAssignmentFromTemplate(
            String appCode, String clientCode, ULong entityId, ULong stageId, String tokenPrefix, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> productService.readById(entityId),
                product -> this.productTemplateRuleService.getUserAssignment(
                        appCode, clientCode, product.getProductTemplateId(), stageId, tokenPrefix, data));
    }
}
