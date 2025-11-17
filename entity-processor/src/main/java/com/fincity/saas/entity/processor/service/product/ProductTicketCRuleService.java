package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.product.ProductTicketCRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRule;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketCUserDistributionService;
import com.google.gson.JsonElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTicketCRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketCRulesRecord,
                ProductTicketCRule,
                ProductTicketCRuleDAO,
                EntityProcessorTicketCUserDistributionsRecord,
                TicketCUserDistribution,
                TicketCUserDistributionDAO> {

    private static final String PRODUCT_TICKET_C_RULE = "productTicketCRule";

    private StageService stageService;

    protected ProductTicketCRuleService(TicketCUserDistributionService ticketCUserDistributionService) {
        super(ticketCUserDistributionService);
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_C_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES;
    }

    @Override
    protected Mono<ProductTicketCRule> checkEntity(ProductTicketCRule entity, ProcessorAccess access) {

        if (entity.isDefault()) return super.checkEntity(entity.setStageId(null), access);

        if (entity.getStageId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        return FlatMapUtil.flatMapMono(() -> super.checkEntity(entity, access), cEntity -> this.stageService
                .getStage(access, cEntity.getProductTemplateId(), cEntity.getStageId())
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID,
                        cEntity.getStageId(),
                        cEntity.getProductTemplateId()))
                .thenReturn(cEntity));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductTicketCRule entity) {

        if (entity.getProductId() != null)
            return Mono.zip(
                    super.evictCache(entity),
                    super.cacheService.evict(
                            this.getCacheName(),
                            super.getCacheKey(
                                    entity.getAppCode(),
                                    entity.getClientCode(),
                                    entity.getProductId(),
                                    entity.getProductTemplateId(),
                                    entity.getStageId())),
                    (baseEvicted, stageEvicted) -> baseEvicted && stageEvicted);

        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductTemplateId(),
                                entity.getStageId())),
                (baseEvicted, stageEvicted) -> baseEvicted && stageEvicted);
    }

    public Mono<Map<Integer, ProductTicketCRule>> getRulesWithOrder(
            ProcessorAccess access, ULong productId, ULong stageId) {

        return super.productService
                .readById(access, productId)
                .flatMap(product -> product.isOverrideCTemplate()
                        ? this.getRulesWithOrderWithTemplateOverride(access, product, stageId)
                        : this.getRulesWithOrderWithTemplateCombine(access, product, stageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketCRuleService.getRulesWithOrder"));
    }

    private Mono<Map<Integer, ProductTicketCRule>> getRulesWithOrderWithTemplateOverride(
            ProcessorAccess access, Product product, ULong stageId) {

        if (!product.isOverrideCTemplate()) return Mono.empty();

        return this.getProductRules(access, product.getId(), product.getProductTemplateId(), stageId)
                .flatMap(rules -> rules.isEmpty()
                        ? this.getProductTemplateRules(access, product.getProductTemplateId(), stageId)
                        : Mono.just(rules))
                .switchIfEmpty(this.getProductTemplateRules(access, product.getProductTemplateId(), stageId))
                .map(rules ->
                        rules.stream().collect(Collectors.toMap(ProductTicketCRule::getOrder, Function.identity())));
    }

    private Mono<Map<Integer, ProductTicketCRule>> getRulesWithOrderWithTemplateCombine(
            ProcessorAccess access, Product product, ULong stageId) {

        if (!product.isOverrideCTemplate()) return Mono.empty();

        return Mono.zip(
                this.getProductRules(access, product.getId(), product.getProductTemplateId(), stageId)
                        .map(rules -> rules.stream()
                                .collect(Collectors.toMap(ProductTicketCRule::getOrder, Function.identity())))
                        .switchIfEmpty(Mono.just(Map.of())),
                this.getProductTemplateRules(access, product.getProductTemplateId(), stageId)
                        .map(rules -> rules.stream()
                                .collect(Collectors.toMap(ProductTicketCRule::getOrder, Function.identity())))
                        .switchIfEmpty(Mono.just(Map.of())),
                (rules, templateRules) -> {
                    int totalSize = rules.size() + templateRules.size();
                    Map<Integer, ProductTicketCRule> combined = LinkedHashMap.newLinkedHashMap(totalSize);

                    AtomicInteger orderCounter = new AtomicInteger(totalSize - 1);

                    rules.entrySet().stream()
                            .sorted(Map.Entry.<Integer, ProductTicketCRule>comparingByKey()
                                    .reversed())
                            .forEach(entry -> combined.put(orderCounter.getAndDecrement(), entry.getValue()));

                    templateRules.entrySet().stream()
                            .sorted(Map.Entry.<Integer, ProductTicketCRule>comparingByKey()
                                    .reversed())
                            .forEach(entry -> combined.put(orderCounter.getAndDecrement(), entry.getValue()));

                    return combined;
                });
    }

    private Mono<List<ProductTicketCRule>> getProductRules(
            ProcessorAccess access, ULong productId, ULong productTemplateId, ULong stageId) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getRules(access, productId, productTemplateId, stageId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productId, productTemplateId, stageId));
    }

    private Mono<List<ProductTicketCRule>> getProductTemplateRules(
            ProcessorAccess access, ULong productTemplateId, ULong stageId) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getRules(access, null, productTemplateId, stageId),
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
                        productRule -> super.ticketCRuleExecutionService.executeRules(
                                access, productRule, tokenPrefix, userId, data),
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
