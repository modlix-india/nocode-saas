package com.fincity.saas.entity.processor.service.product;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductTicketCRuleDAO;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRuleDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTicketCRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketCRulesRecord, ProductTicketCRuleDto, ProductTicketCRuleDAO> {

    private static final String PRODUCT_TICKET_C_RULE = "productTicketCRule";

    private StageService stageService;

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_C_RULE;
    }

    @Override
    protected Mono<ProductTicketCRuleDto> checkEntity(ProductTicketCRuleDto entity, ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(() -> super.checkEntity(entity, access), cEntity -> this.stageService
                .getStage(access, cEntity.getProductTemplateId(), cEntity.getStageId())
                .thenReturn(cEntity));
    }

    public Mono<Map<Integer, ProductTicketCRuleDto>> getRulesWithOrder(
            ProcessorAccess access, ULong productId, ULong stageId) {

        return super.productService
                .readById(access, productId)
                .flatMap(product -> product.isOverrideTemplate()
                        ? this.getRulesWithOrderWithTemplateOverride(access, product, stageId)
                        : this.getRulesWithOrderWithTemplateCombine(access, product, stageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketCRuleService.getRulesWithOrder"));
    }

    private Mono<Map<Integer, ProductTicketCRuleDto>> getRulesWithOrderWithTemplateOverride(
            ProcessorAccess access, Product product, ULong stageId) {

        if (!product.isOverrideTemplate()) return Mono.empty();

        return this.getProductRules(access, product.getId(), product.getProductTemplateId(), stageId)
                .switchIfEmpty(this.getProductTemplateRules(access, product.getProductTemplateId(), stageId));
    }

    private Mono<Map<Integer, ProductTicketCRuleDto>> getRulesWithOrderWithTemplateCombine(
            ProcessorAccess access, Product product, ULong stageId) {

        if (!product.isOverrideTemplate()) return Mono.empty();

        return Mono.zip(
                this.getProductRules(access, product.getId(), product.getProductTemplateId(), stageId)
                        .switchIfEmpty(Mono.just(Map.of())),
                this.getProductTemplateRules(access, product.getProductTemplateId(), stageId)
                        .switchIfEmpty(Mono.just(Map.of())),
                (rules, templateRules) -> {
                    int totalSize = rules.size() + templateRules.size();
                    Map<Integer, ProductTicketCRuleDto> combined = new LinkedHashMap<>(totalSize);

                    AtomicInteger orderCounter = new AtomicInteger(totalSize - 1);

                    rules.entrySet().stream()
                            .sorted(Map.Entry.<Integer, ProductTicketCRuleDto>comparingByKey()
                                    .reversed())
                            .forEach(entry -> combined.put(orderCounter.getAndDecrement(), entry.getValue()));

                    templateRules.entrySet().stream()
                            .sorted(Map.Entry.<Integer, ProductTicketCRuleDto>comparingByKey()
                                    .reversed())
                            .forEach(entry -> combined.put(orderCounter.getAndDecrement(), entry.getValue()));

                    return combined;
                });
    }

    private Mono<Map<Integer, ProductTicketCRuleDto>> getProductRules(
            ProcessorAccess access, ULong productId, ULong productTemplateId, ULong stageId) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getRules(access, productId, productTemplateId, stageId)
                        .map(rules -> rules.stream()
                                .collect(Collectors.toMap(ProductTicketCRuleDto::getOrder, Function.identity()))),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productId, productTemplateId, stageId));
    }

    private Mono<Map<Integer, ProductTicketCRuleDto>> getProductTemplateRules(
            ProcessorAccess access, ULong productTemplateId, ULong stageId) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getRules(access, null, productTemplateId, stageId)
                        .map(rules -> rules.stream()
                                .collect(Collectors.toMap(ProductTicketCRuleDto::getOrder, Function.identity()))),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productTemplateId, stageId));
    }

    public Mono<ULong> getUserAssignment(
            ProcessorAccess access,
            ULong productId,
            ULong stageId,
            String tokenPrefix,
            ULong userId,
            JsonElement data) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getRulesWithOrder(access, productId, stageId),
                        productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, userId, data),
                        (productRule, eRule) -> super.updateInternalForOutsideUser(eRule),
                        (productRule, eRule, uRule) -> {
                            ULong assignedUserId = uRule.getLastAssignedUserId();
                            if (assignedUserId == null || assignedUserId.equals(ULong.valueOf(0))) return Mono.empty();
                            return Mono.just(assignedUserId);
                        })
                .onErrorResume(e -> Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductStageRuleService.getUserAssignment"));
    }
}
