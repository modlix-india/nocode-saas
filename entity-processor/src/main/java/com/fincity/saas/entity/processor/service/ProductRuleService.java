package com.fincity.saas.entity.processor.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductRuleDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRuleRequest;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

@Service
public class ProductRuleService
        extends RuleConfigService<ProductRuleRequest, EntityProcessorProductRulesRecord, ProductRule, ProductRuleDAO> {

    private static final String PRODUCT_RULE_CONFIG = "productRuleConfig";

    private ProductService productService;
    private ValueTemplateRuleService valueTemplateRuleService;

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Lazy
    @Autowired
    private void setValueTemplateRuleService(ValueTemplateRuleService valueTemplateRuleService) {
        this.valueTemplateRuleService = valueTemplateRuleService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_RULE_CONFIG;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE;
    }

    @Override
    protected Mono<ULong> getEntityId(String appCode, String clientCode, Identity productId) {
        return productService.readWithAccess(productId).map(Product::getId);
    }

    @Override
    protected Mono<ULong> getValueTemplateId(String appCode, String clientCode, Identity entityId) {
        return productService.readIdentityInternal(entityId).map(Product::getValueTemplateId);
    }

    @Override
    protected Mono<ProductRule> createNewInstance() {
        return Mono.just(new ProductRule());
    }

    @Override
    protected Mono<ULong> getUserAssignment(
            String appCode, String clientCode, ULong productId, Platform platform, JsonElement data) {

        return FlatMapUtil.flatMapMono(
                        () -> this.read(appCode, clientCode, productId, platform),
                        productRule -> super.ruleExecutionService.executeRules(productRule, data))
                .switchIfEmpty(this.getUserAssignmentFromTemplate(appCode, clientCode, productId, platform, data));
    }

    private Mono<ULong> getUserAssignmentFromTemplate(
            String appCode, String clientCode, ULong productId, Platform platform, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> productService.readById(productId),
                product -> this.valueTemplateRuleService.getUserAssignment(
                        appCode, clientCode, product.getValueTemplateId(), platform, data));
    }
}
