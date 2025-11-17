package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.entity.processor.dao.product.ProductTicketRuRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketRuUserDistributionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTicketRuRuleService
        extends BaseRuleService<
                EntityProcessorProductTicketRuRulesRecord,
                ProductTicketRuRule,
                ProductTicketRuRuleDAO,
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRuUserDistribution,
                TicketRuUserDistributionDAO> {

    private static final String PRODUCT_TICKET_RU_RULE = "productTicketRURule";
    private static final String CONDITION_CACHE = "ruleConditionCache";

    protected ProductTicketRuRuleService(TicketRuUserDistributionService ticketRUUserDistributionService) {
        super(ticketRUUserDistributionService);
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TICKET_RU_RULE;
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

    public Mono<List<ProductTicketRuRule>> getConditionsForUserInternal(ProcessorAccess access, boolean isEdit) {
        return FlatMapUtil.flatMapMono(
                () -> this.userDistributionService.getUserForClient(access),
                userInfo -> this.dao.getUserConditions(isEdit, userInfo).collectList());
    }

    public Mono<AbstractCondition> getUserReadConditions(ProcessorAccess access) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getConditionCacheName(access.getAppCode(), access.getClientCode()),
                () -> this.getConditionsForUserInternal(access, false).flatMap(this::getUserReadConditions),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), access.getUserId()));
    }

    private Mono<AbstractCondition> getUserReadConditions(List<ProductTicketRuRule> rules) {
        if (rules.isEmpty()) return Mono.empty();

        ProductTemplateMaps productTemplateMaps = this.buildRuleMaps(rules);

        List<AbstractCondition> orBlocks = this.buildProductConditionBlocks(productTemplateMaps);

        this.addTemplateOnlyBlocks(productTemplateMaps, orBlocks);

        return orBlocks.isEmpty() ? Mono.empty() : Mono.just(ComplexCondition.or(orBlocks));
    }

    private ProductTemplateMaps buildRuleMaps(List<ProductTicketRuRule> rules) {

        Map<ULong, List<ProductTicketRuRule>> productMap = new HashMap<>();
        Map<ULong, List<ProductTicketRuRule>> templateMap = new HashMap<>();
        Set<ULong> usedTemplates = new HashSet<>();

        for (var rule : rules) {
            productMap
                    .computeIfAbsent(rule.getProductId(), x -> new ArrayList<>())
                    .add(rule);

            ULong templateId = rule.getProductTemplateId();
            if (templateId != null)
                templateMap.computeIfAbsent(templateId, x -> new ArrayList<>()).add(rule);
        }

        return new ProductTemplateMaps(productMap, templateMap, usedTemplates);
    }

    private List<AbstractCondition> buildProductConditionBlocks(ProductTemplateMaps productTemplateMaps) {

        List<AbstractCondition> blocks = new ArrayList<>();

        for (var entry : productTemplateMaps.productMap.entrySet()) {
            List<ProductTicketRuRule> productRules = entry.getValue();
            AbstractCondition block = this.buildSingleProductBlock(productRules, productTemplateMaps);
            blocks.add(block);
        }

        return blocks;
    }

    private AbstractCondition buildSingleProductBlock(
            List<ProductTicketRuRule> rules, ProductTemplateMaps productTemplateMaps) {

        var first = rules.getFirst();
        boolean override = first.isOverrideRuTemplate();

        List<AbstractCondition> productConds =
                rules.stream().map(ProductTicketRuRule::getConditionWithProduct).toList();

        return override
                ? ComplexCondition.and(productConds)
                : this.mergeProductAndTemplateConditions(
                        first.getProductTemplateId(), productConds, productTemplateMaps);
    }

    private AbstractCondition mergeProductAndTemplateConditions(
            ULong templateId, List<AbstractCondition> productConds, ProductTemplateMaps productTemplateMaps) {

        if (templateId == null || !productTemplateMaps.templateMap.containsKey(templateId))
            return ComplexCondition.and(productConds);

        List<AbstractCondition> merged = new ArrayList<>(productConds);

        List<AbstractCondition> templateConds = productTemplateMaps.templateMap.get(templateId).stream()
                .map(ProductTicketRuRule::getConditionWithProductTemplate)
                .toList();

        merged.addAll(templateConds);

        productTemplateMaps.usedTemplates.add(templateId);

        return ComplexCondition.and(merged);
    }

    private void addTemplateOnlyBlocks(ProductTemplateMaps productTemplateMaps, List<AbstractCondition> target) {

        for (var entry : productTemplateMaps.templateMap.entrySet()) {

            if (productTemplateMaps.usedTemplates.contains(entry.getKey())) continue;

            List<AbstractCondition> templateConds = entry.getValue().stream()
                    .map(ProductTicketRuRule::getConditionWithProductTemplate)
                    .toList();

            target.add(ComplexCondition.and(templateConds));
        }
    }

    private record ProductTemplateMaps(
            Map<ULong, List<ProductTicketRuRule>> productMap,
            Map<ULong, List<ProductTicketRuRule>> templateMap,
            Set<ULong> usedTemplates) {}
}
