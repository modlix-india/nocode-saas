package com.fincity.saas.entity.processor.service.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.entity.processor.dao.product.ProductTicketRuRuleDAO;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRuUserDistributionService;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ProductTicketRuRuleService
        extends
        BaseRuleService<EntityProcessorProductTicketRuRulesRecord, ProductTicketRuRule, ProductTicketRuRuleDAO, TicketRuUserDistribution> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRuRule";
    private static final String CONDITION_CACHE = "ruleConditionCache";

    @Getter
    private TicketRuUserDistributionService userDistributionService;

    @Autowired
    private void setUserDistributionService(TicketRuUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_RU_RULE;
    }

    private String getConditionCacheName(String appCode, String clientCode) {
        return super.getCacheName(CONDITION_CACHE, appCode, clientCode);
    }

    private Mono<Boolean> evictConditionCache(String appCode, String clientCode) {
        return super.cacheService.evictAll(this.getConditionCacheName(appCode, clientCode));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductTicketRuRule entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictConditionCache(entity.getAppCode(), entity.getClientCode()),
                (baseEvicted, conditionEvicted) -> baseEvicted && conditionEvicted);
    }

    @Override
    public Mono<ProductTicketRuRule> create(ProductTicketRuRule entity) {
        return super.create(entity)
                .flatMap(created -> this.evictConditionCache(entity.getAppCode(), entity.getClientCode())
                        .map(evicted -> created));
    }

    @IgnoreGeneration
    public Mono<AbstractCondition> getUserReadConditions(ProcessorAccess access) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getConditionCacheName(access.getAppCode(), access.getClientCode()),
                () -> this.getUserReadConditionInternal(access),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), access.getUserId()));
    }

    private Mono<AbstractCondition> getUserReadConditionInternal(ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                () -> this.getConditionsForUserInternal(access, false),
                rules -> this.getUserReadConditions(access, rules));
    }

    private Mono<List<ProductTicketRuRule>> getConditionsForUserInternal(ProcessorAccess access, boolean isEdit) {
        return FlatMapUtil.flatMapMono(
                () -> this.userDistributionService.getUserForClient(access),
                userInfo -> this.dao.getUserConditions(access, isEdit, userInfo).collectList());
    }

    private Mono<AbstractCondition> getUserReadConditions(ProcessorAccess access, List<ProductTicketRuRule> rules) {
        if (rules.isEmpty())
            return Mono.empty();

        ProductTemplateMaps productTemplateMaps = this.buildRuleMaps(rules);

        // Fetch all products to get their overrideRuTemplate flags
        Set<ULong> productIds = productTemplateMaps.productMap.keySet();

        return this.fetchProducts(access, productIds)
                .map(productsMap -> {
                    List<AbstractCondition> productConditionBlocks = this
                            .buildProductConditionBlocks(productTemplateMaps, productsMap);

                    List<AbstractCondition> productTemplateBlocks = this.addTemplateOnlyBlocks(productTemplateMaps);

                    if (productConditionBlocks.isEmpty() && !productTemplateBlocks.isEmpty())
                        return ComplexCondition.or(productTemplateBlocks);
                    if (productTemplateBlocks.isEmpty() && !productConditionBlocks.isEmpty())
                        return ComplexCondition.or(productConditionBlocks);

                    productConditionBlocks.addAll(productTemplateBlocks);

                    return ComplexCondition.or(productConditionBlocks);
                })
                .flatMap(condition -> condition == null ? Mono.empty() : Mono.just(condition));
    }

    private ProductTemplateMaps buildRuleMaps(List<ProductTicketRuRule> rules) {

        Map<ULong, List<ProductTicketRuRule>> productMap = new HashMap<>();
        Map<ULong, List<ProductTicketRuRule>> templateMap = new HashMap<>();
        Set<ULong> usedTemplates = new HashSet<>();

        for (var rule : rules) {
            ULong productId = rule.getProductId();
            if (productId != null)
                productMap.computeIfAbsent(productId, x -> new ArrayList<>()).add(rule);

            ULong templateId = rule.getProductTemplateId();
            if (templateId != null)
                templateMap.computeIfAbsent(templateId, x -> new ArrayList<>()).add(rule);
        }

        return new ProductTemplateMaps(productMap, templateMap, usedTemplates);
    }

    private Mono<Map<ULong, Product>> fetchProducts(ProcessorAccess access, Set<ULong> productIds) {
        if (productIds.isEmpty())
            return Mono.just(new HashMap<>());

        return Flux.fromIterable(productIds)
                .flatMap(productId -> this.productService
                        .readById(access, productId)
                        .map(product -> Map.entry(productId, product))
                        .onErrorResume(e -> Mono.empty()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private List<AbstractCondition> buildProductConditionBlocks(
            ProductTemplateMaps productTemplateMaps, Map<ULong, Product> productsMap) {

        List<AbstractCondition> blocks = new ArrayList<>();

        for (var entry : productTemplateMaps.productMap.entrySet()) {
            ULong productId = entry.getKey();
            List<ProductTicketRuRule> productRules = entry.getValue();
            Product product = productsMap.get(productId);
            AbstractCondition block = this.buildSingleProductBlock(productRules, productTemplateMaps, product);
            blocks.add(block);
        }

        return blocks;
    }

    private AbstractCondition buildSingleProductBlock(
            List<ProductTicketRuRule> rules, ProductTemplateMaps productTemplateMaps, Product product) {

        ProductTicketRuRule first = rules.getFirst();
        // Use overrideRuTemplate from product, default to false if product is not found
        boolean override = product != null && product.isOverrideRuTemplate();

        List<AbstractCondition> productConditions = rules.stream().map(ProductTicketRuRule::getConditionWithProduct)
                .toList();

        return override
                ? this.takeOnlyProductCondition(first.getProductTemplateId(), productConditions, productTemplateMaps)
                : this.mergeProductAndTemplateConditions(
                        first.getProductTemplateId(), productConditions, productTemplateMaps);
    }

    private AbstractCondition takeOnlyProductCondition(
            ULong templateId, List<AbstractCondition> productConditions, ProductTemplateMaps productTemplateMaps) {

        if (templateId == null || !productTemplateMaps.templateMap.containsKey(templateId))
            return ComplexCondition.or(productConditions);

        productTemplateMaps.usedTemplates.add(templateId);

        return ComplexCondition.or(productConditions);
    }

    private AbstractCondition mergeProductAndTemplateConditions(
            ULong templateId, List<AbstractCondition> productConditions, ProductTemplateMaps productTemplateMaps) {

        if (templateId == null || !productTemplateMaps.templateMap.containsKey(templateId))
            return ComplexCondition.or(productConditions);

        List<AbstractCondition> templateConditions = productTemplateMaps.templateMap.get(templateId).stream()
                .map(ProductTicketRuRule::getConditionWithProductTemplate)
                .toList();

        productTemplateMaps.usedTemplates.add(templateId);

        return ComplexCondition.or(ComplexCondition.or(productConditions), ComplexCondition.or(templateConditions));
    }

    private List<AbstractCondition> addTemplateOnlyBlocks(ProductTemplateMaps productTemplateMaps) {

        List<AbstractCondition> result = new ArrayList<>();

        for (var entry : productTemplateMaps.templateMap.entrySet()) {

            if (productTemplateMaps.usedTemplates.contains(entry.getKey()))
                continue;

            List<AbstractCondition> templateConditions = entry.getValue().stream()
                    .map(ProductTicketRuRule::getConditionWithProductTemplate)
                    .toList();

            result.add(ComplexCondition.or(templateConditions));
        }

        return result;
    }

    private record ProductTemplateMaps(
            Map<ULong, List<ProductTicketRuRule>> productMap,
            Map<ULong, List<ProductTicketRuRule>> templateMap,
            Set<ULong> usedTemplates) {
    }
}
