package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductRuleDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleExecutionService;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
import com.google.gson.JsonElement;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductRuleService
        extends RuleConfigService<ProductRuleRequest, EntityProcessorProductRulesRecord, ProductRule, ProductRuleDAO> {

    private static final String PRODUCT_RULE_CONFIG = "productRuleConfig";

    private ProductService productService;
    private RuleExecutionService ruleExecutionService;

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Autowired
    private void setRuleExecutionService(RuleExecutionService ruleExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_RULE_CONFIG;
    }

    @Override
    protected Mono<ULong> getEntityId(String appCode, String clientCode, ULong userId, Identity productId) {
        return productService.readWithAccess(productId).map(Product::getId);
    }

    @Override
    protected Mono<ProductRule> createNewInstance() {
        return Mono.just(new ProductRule());
    }

    public Mono<ULong> getUserAssignment(String appCode, String clientCode, ULong productId, Platform platform, JsonElement data) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(appCode, clientCode, productId, platform),
                productRule -> {
                    return ruleExecutionService.executeRules(productRule, data);
                }
        )
    }
}
