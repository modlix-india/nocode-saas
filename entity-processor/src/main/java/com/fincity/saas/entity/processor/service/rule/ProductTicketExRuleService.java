package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.rule.ProductTicketExRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductTicketExRuleService
        extends BaseUpdatableService<
                EntityProcessorProductTicketExRulesRecord,
                ProductTicketExRule,
                ProductTicketExRuleDAO> {

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
        return "productTicketExRule";
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TICKET_EX_RULES;
    }

    @Override
    protected Mono<ProductTicketExRule> checkEntity(ProductTicketExRule entity, ProcessorAccess access) {

        if (StringUtil.safeIsBlank(entity.getSource())) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "Source",
                    this.getEntityName());
        }

        if (entity.getExpiryDays() == null || entity.getExpiryDays() <= 0) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "Expiry Days",
                    this.getEntityName());
        }

        if (entity.getProductId() != null && entity.getProductTemplateId() != null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS,
                    "Product Id and Product Template Id (only one allowed)",
                    this.getEntityName());
        }

        if (entity.getProductId() == null && entity.getProductTemplateId() == null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "Product Id or Product Template Id",
                    this.getEntityName());
        }

        if (entity.getProductTemplateId() != null) {
            entity.setProductId(null);
            return this.productTemplateService
                    .readByIdentity(Identity.of(entity.getProductTemplateId().toBigInteger()))
                    .thenReturn(entity);
        }

        entity.setProductTemplateId(null);
        return this.productService
                .readByIdentity(Identity.of(entity.getProductId().toBigInteger()))
                .thenReturn(entity);
    }

    @Override
    protected Mono<ProductTicketExRule> updatableEntity(ProductTicketExRule rule) {
        return super.updatableEntity(rule).map(existing -> {
            existing.setProductId(rule.getProductId());
            existing.setProductTemplateId(rule.getProductTemplateId());
            existing.setSource(rule.getSource());
            existing.setExpiryDays(rule.getExpiryDays());
            return existing;
        });
    }

    @Override
    public Mono<ProductTicketExRule> create(ProductTicketExRule entity) {
        return super.create(entity)
                .flatMap(created -> this.evictActiveRulesCache(created).thenReturn(created));
    }

    @Override
    public Mono<ProductTicketExRule> update(ProductTicketExRule entity) {
        return super.update(entity)
                .flatMap(updated -> this.evictActiveRulesCache(updated).thenReturn(updated));
    }

    public Mono<List<ProductTicketExRule>> getActiveRules(ProcessorAccess access) {

        String cacheKey = this.getCacheKey(access.getAppCode(), access.getClientCode(), "activeRules");

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.findActiveByAppCodeAndClientCode(access.getAppCode(), access.getClientCode()),
                cacheKey);
    }

    private Mono<Boolean> evictActiveRulesCache(ProductTicketExRule entity) {
        String cacheKey = this.getCacheKey(entity.getAppCode(), entity.getClientCode(), "activeRules");
        return this.cacheService.evict(this.getCacheName(), cacheKey);
    }
}
