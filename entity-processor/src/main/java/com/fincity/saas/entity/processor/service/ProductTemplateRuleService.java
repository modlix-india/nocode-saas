package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateRuleService
        extends RuleService<EntityProcessorProductTemplateRulesRecord, ProductTemplateRule, ProductTemplateRuleDAO> {

    private static final String PRODUCT_TEMPLATE_RULE = "valueTemplateRule";

    private ProductTemplateService productTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE_RULE;
    }

    @Override
    protected Mono<ProductTemplateRule> createFromRequest(RuleRequest ruleRequest) {
        return Mono.just(new ProductTemplateRule().of(ruleRequest));
    }

    @Override
    protected Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId) {
        return productTemplateService.checkAndUpdateIdentityWithAccess(access, entityId);
    }

    @Override
    protected String getEntityRefName() {
        return productTemplateService.getEntityName();
    }

    @Override
    protected Mono<Set<ULong>> getStageIds(String appCode, String clientCode, Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                        () -> productTemplateService.readIdentityInternal(entityId),
                        productTemplate -> super.stageService.getAllStages(
                                appCode,
                                clientCode,
                                productTemplate.getId(),
                                stageIds != null ? stageIds.toArray(new ULong[0]) : null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateRuleService.getStageIds"));
    }

    @Override
    protected Mono<ULong> getStageId(String appCode, String clientCode, Identity entityId, ULong stageId) {
        return FlatMapUtil.flatMapMono(
                        () -> productTemplateService.readIdentityInternal(entityId),
                        productTemplate ->
                                super.stageService.getStage(appCode, clientCode, productTemplate.getId(), stageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateRuleService.getStageId"));
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
                        productTemplateRules -> super.ruleExecutionService.executeRules(
                                productTemplateRules, tokenPrefix, userId, data),
                        (productTemplateRules, eRule) -> super.update(eRule).flatMap(rule -> {
                            ULong assignedUserId = rule.getLastAssignedUserId();
                            if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0))) return Mono.empty();

                            return Mono.just(assignedUserId);
                        }))
                .onErrorResume(e -> Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateRuleService.getUserAssignment"));
    }
}
