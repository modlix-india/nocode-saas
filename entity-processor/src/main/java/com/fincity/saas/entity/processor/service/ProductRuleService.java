package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.ProductRuleDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRuleRequest;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
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

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
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
}
