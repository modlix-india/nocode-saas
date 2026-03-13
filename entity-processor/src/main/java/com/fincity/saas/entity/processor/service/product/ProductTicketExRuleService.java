package com.fincity.saas.entity.processor.service.product;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.product.ProductTicketExRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketExRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import java.time.LocalDateTime;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTicketExRuleService
        extends BaseUpdatableService<
                EntityProcessorProductTicketExRulesRecord, ProductTicketExRule, ProductTicketExRuleDAO> {

    private static final String CACHE_NAME = "productTicketExRule";

    private ProductService productService;
    private ProductTemplateService productTemplateService;

    @Autowired
    @Lazy
    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Autowired
    @Lazy
    public void setProductTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected String getCacheName() {
        return CACHE_NAME;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_EX_RULE;
    }

    @Override
    protected Mono<ProductTicketExRule> checkEntity(ProductTicketExRule entity, ProcessorAccess access) {

        if (entity.getProductId() == null && entity.getProductTemplateId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_PRODUCT_MISSING);

        if (entity.getProductId() != null && entity.getProductTemplateId() != null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_PRODUCT_MISSING);

        if (StringUtil.safeIsBlank(entity.getSource()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "source",
                    "Expiration Rule");

        if (entity.getExpiryDays() == null || entity.getExpiryDays() <= 0)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "expiryDays",
                    "Expiration Rule");

        if (entity.getProductId() != null) {
            return this.productService
                    .readById(access, entity.getProductId())
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.IDENTITY_MISSING,
                            this.productService.getEntityName()))
                    .map(product -> entity.setProductTemplateId(null))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.checkEntity"));
        }

        return this.productTemplateService
                .readById(access, entity.getProductTemplateId())
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.PRODUCT_TEMPLATE_MISSING,
                        ""))
                .map(template -> entity.setProductId(null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.checkEntity"));
    }

    @Override
    protected Mono<ProductTicketExRule> updatableEntity(ProductTicketExRule entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setExpiryDays(entity.getExpiryDays());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<ProductTicketExRule> create(ProductTicketExRule entity) {
        return super.create(entity)
                .flatMap(created -> this.hasAccess()
                        .flatMap(access -> this.recalculateExpiresOnForRule(access, created)
                                .thenReturn(created)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.create"));
    }

    @Override
    public Mono<ProductTicketExRule> update(ProductTicketExRule entity) {
        return super.update(entity)
                .flatMap(updated -> this.hasAccess()
                        .flatMap(access -> this.recalculateExpiresOnForRule(access, updated)
                                .thenReturn(updated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.update"));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductTicketExRule entity) {
        return Mono.zip(
                        super.evictCache(entity),
                        this.evictRuleCache(entity),
                        (baseEvicted, ruleEvicted) -> baseEvicted && ruleEvicted)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.evictCache"));
    }

    private Mono<Boolean> evictRuleCache(ProductTicketExRule entity) {
        if (entity.getProductId() != null) {
            return this.cacheService.evict(
                    CACHE_NAME,
                    this.getCacheKey(
                            entity.getAppCode(),
                            entity.getClientCode(),
                            "product",
                            entity.getProductId(),
                            entity.getSource()));
        }
        return this.cacheService.evict(
                CACHE_NAME,
                this.getCacheKey(
                        entity.getAppCode(),
                        entity.getClientCode(),
                        "template",
                        entity.getProductTemplateId(),
                        entity.getSource()));
    }

    public Mono<LocalDateTime> computeExpiresOn(ProcessorAccess access, ULong productId, String source) {

        if (productId == null || StringUtil.safeIsBlank(source)) return Mono.empty();

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.findProductRule(access, productId, source),
                        rule -> {
                            if (rule != null) return Mono.just(rule);
                            return this.productService
                                    .readById(access, productId)
                                    .flatMap(product -> product.getProductTemplateId() != null
                                            ? this.findTemplateRule(
                                                    access, product.getProductTemplateId(), source)
                                            : Mono.empty());
                        },
                        (productRule, resolvedRule) -> {
                            ProductTicketExRule effectiveRule =
                                    resolvedRule != null ? resolvedRule : productRule;
                            if (effectiveRule == null) return Mono.empty();
                            return Mono.just(LocalDateTime.now().plusDays(effectiveRule.getExpiryDays()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.computeExpiresOn"));
    }

    private Mono<ProductTicketExRule> findProductRule(ProcessorAccess access, ULong productId, String source) {
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME,
                () -> this.dao.findActiveRuleByProduct(access, productId, source),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), "product", productId, source));
    }

    private Mono<ProductTicketExRule> findTemplateRule(
            ProcessorAccess access, ULong productTemplateId, String source) {
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME,
                () -> this.dao.findActiveRuleByTemplate(access, productTemplateId, source),
                this.getCacheKey(
                        access.getAppCode(), access.getClientCode(), "template", productTemplateId, source));
    }

    public Mono<Void> recalculateExpiresOnForRule(ProcessorAccess access, ProductTicketExRule rule) {

        return this.dao
                .recalculateExpiresOn(rule)
                .then()
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.recalculateExpiresOnForRule"));
    }
}
