package com.fincity.saas.entity.processor.service;

import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductStageRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

@Service
public class ProductStageRuleService
        extends RuleService<EntityProcessorProductStageRulesRecord, ProductStageRule, ProductStageRuleDAO> {

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
        return Mono.just(new ProductStageRule().of(ruleRequest));
    }

    @Override
    protected Mono<Identity> getEntityId(Identity entityId) {
        return productService.checkAndUpdateIdentity(entityId);
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(String appCode, String clientCode, Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityInternal(entityId),
                productStageRule -> productService.readById(productStageRule.getEntityId()),
                (productStageRule, product) -> super.stageService.getAllStages(
                        appCode,
                        clientCode,
                        product.getProductTemplateId(),
                        stageIds != null ? stageIds.toArray(new ULong[0]) : null));
    }

    @Override
    protected Mono<ULong> getStageId(String appCode, String clientCode, Identity entityId, ULong stageId) {
        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityInternal(entityId),
                productStageRule -> productService.readById(productStageRule.getEntityId()),
                (productStageRule, product) ->
                        super.stageService.getStage(appCode, clientCode, product.getProductTemplateId(), stageId));
    }

    @Override
    public Mono<ULong> getUserAssignment(
            String appCode,
            String clientCode,
            ULong entityId,
            ULong stageId,
            String tokenPrefix,
            ULong userId,
            JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getRuleWithOrder(appCode, clientCode, entityId, stageId),
                        productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, userId, data),
                        (productRule, eRule) -> super.update(eRule).flatMap(rule -> {
                            ULong assignedUserId = rule.getLastAssignedUserId();
                            if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0))) return Mono.empty();
                            return Mono.just(assignedUserId);
                        }))
                .switchIfEmpty(this.getUserAssignmentFromTemplate(
                        appCode, clientCode, entityId, stageId, tokenPrefix, userId, data))
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<ULong> getUserAssignmentFromTemplate(
            String appCode,
            String clientCode,
            ULong entityId,
            ULong stageId,
            String tokenPrefix,
            ULong userId,
            JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> productService.readById(entityId),
                product -> this.productTemplateRuleService.getUserAssignment(
                        appCode, clientCode, product.getProductTemplateId(), stageId, tokenPrefix, userId, data));
    }
}
