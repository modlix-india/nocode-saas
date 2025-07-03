package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.ProductTemplateType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductTemplateRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateService
        extends BaseUpdatableService<EntityProcessorProductTemplatesRecord, ProductTemplate, ProductTemplateDAO>
        implements IEntitySeries {

    private static final String PRODUCT_TEMPLATE = "productTemplate";
    private ProductService productService;

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_TEMPLATE;
    }

    public Mono<ProductTemplate> create(ProductTemplateRequest productTemplateRequest) {

        ProductTemplateType productTemplateType = productTemplateRequest.getProductTemplateType();

        if (productTemplateType == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.PRODUCT_TEMPLATE_TYPE_MISSING);

        ProductTemplate productTemplate = ProductTemplate.of(productTemplateRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> {
                    productTemplate.setAppCode(access.getAppCode());
                    productTemplate.setClientCode(access.getClientCode());

                    return super.create(productTemplate);
                },
                (access, created) -> (productTemplateRequest.getProductId() == null
                                || productTemplateRequest.getProductId().isNull())
                        ? Mono.just(created)
                        : this.updateDependentServices(created, productTemplateRequest.getProductId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateService.create"));
    }

    public Mono<ProductTemplate> attachEntity(Identity identity, ProductTemplateRequest productTemplateRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.readIdentityWithAccess(identity),
                productTemplate ->
                        this.updateDependentServices(productTemplate, productTemplateRequest.getProductId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateService.attachEntity"));
    }

    private Mono<ProductTemplate> updateDependentServices(ProductTemplate productTemplate, Identity productId) {
        return productService.setProductTemplate(productId, productTemplate);
    }
}
