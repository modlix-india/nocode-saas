package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.ProductStageRuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductStageRuleService
        extends RuleService<EntityProcessorProductRulesRecord, ProductStageRule, ProductStageRuleDAO> {

    private static final String PRODUCT_RULE_CONFIG = "productRuleConfig";

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
    protected Mono<ProductStageRule> createFromRequest(RuleRequest ruleRequest) {
        return FlatMapUtil.flatMapMono(
                () -> productService.checkAndUpdateIdentity(ruleRequest.getEntityId()),
                productId -> Mono.just(new ProductStageRule().of(ruleRequest).setEntityId(productId.getULongId())));
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_RULE_CONFIG;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE;
    }

    //    @Override
    //    protected Mono<ULong> getEntityId(String appCode, String clientCode, Identity productId) {
    //        return productService.readWithAccess(productId).map(Product::getId);
    //    }
    //
    //    @Override
    //    protected Mono<ULong> getValueTemplateId(String appCode, String clientCode, Identity entityId) {
    //        return productService.readIdentityInternal(entityId).map(Product::getValueTemplateId);
    //    }
    //
    //    @Override
    //    protected Mono<ProductStageRule> createNewInstance() {
    //        return Mono.just(new ProductStageRule());
    //    }
    //
    //    @Override
    //    protected Mono<ULong> getUserAssignment(
    //            String appCode,
    //            String clientCode,
    //            ULong productId,
    //            Platform platform,
    //            String tokenPrefix,
    //            JsonElement data) {
    //        return FlatMapUtil.flatMapMono(
    //                        () -> this.read(appCode, clientCode, productId, platform),
    //                        productRule -> super.ruleExecutionService.executeRules(productRule, tokenPrefix, data))
    //                .switchIfEmpty(this.getUserAssignmentFromTemplate(
    //                        appCode, clientCode, productId, platform, tokenPrefix, data));
    //    }
    //
    //    private Mono<ULong> getUserAssignmentFromTemplate(
    //            String appCode,
    //            String clientCode,
    //            ULong productId,
    //            Platform platform,
    //            String tokenPrefix,
    //            JsonElement data) {
    //        return FlatMapUtil.flatMapMono(
    //                () -> productService.readById(productId),
    //                product -> this.productTemplateRuleService.getUserAssignment(
    //                        appCode, clientCode, product.getValueTemplateId(), platform, tokenPrefix, data));
    //    }
}
