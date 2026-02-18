package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.ProductTicketExRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;

@Service
public class ProductTicketExRuleService
        extends BaseUpdatableService<
                EntityProcessorProductTicketExRulesRecord, ProductTicketExRule, ProductTicketExRuleDAO> {

    private static final String PRODUCT_TICKET_EX_RULE_CACHE = "productTicketExRule";

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
        return PRODUCT_TICKET_EX_RULE_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_EX_RULES;
    }

    public Mono<List<ProductTicketExRule>> getActiveRules(ProcessorAccess access) {
        return cacheService.cacheValueOrGet(
                getCacheName(),
                () -> dao.findActiveByAppCodeAndClientCode(access.getAppCode(), access.getClientCode()).collectList(),
                getCacheKey(access.getAppCode(), access.getClientCode(), "activeRules"));
    }

    @Override
    protected Mono<Boolean> evictCache(ProductTicketExRule entity) {
        return Mono.zip(
                super.evictCache(entity),
                cacheService.evict(
                        getCacheName(), getCacheKey(entity.getAppCode(), entity.getClientCode(), "activeRules")),
                (baseEvicted, rulesEvicted) -> baseEvicted && rulesEvicted);
    }

    @Override
    protected Mono<ProductTicketExRule> checkEntity(ProductTicketExRule entity, ProcessorAccess access) {
        boolean hasProduct = entity.getProductId() != null;
        boolean hasTemplate = entity.getProductTemplateId() != null;
        if (!hasProduct && !hasTemplate)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Product or product template");

        if (entity.getSource() == null || entity.getSource().isBlank())
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Source");

        if (entity.getExpiryDays() == null || entity.getExpiryDays() <= 0)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "Expiry days must be positive");

        if (hasTemplate) {
            entity.setProductId(null);
            return productTemplateService
                    .readById(access, entity.getProductTemplateId())
                    .switchIfEmpty(msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.PRODUCT_TEMPLATE_MISSING,
                            entity.getProductTemplateId()))
                    .thenReturn(entity)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.checkEntity"));
        }

        entity.setProductTemplateId(null);
        return productService
                .readById(access, entity.getProductId())
                .switchIfEmpty(msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        "Product",
                        entity.getProductId()))
                .thenReturn(entity)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.checkEntity"));
    }

    @Override
    protected Mono<ProductTicketExRule> create(ProcessorAccess access, ProductTicketExRule entity) {
        return super.create(access, entity)
                .flatMap(created -> evictCache(created).map(evicted -> created));
    }

    @Override
    protected Mono<ProductTicketExRule> updatableEntity(ProductTicketExRule entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setExpiryDays(entity.getExpiryDays());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTicketExRuleService.updatableEntity"));
    }
}
