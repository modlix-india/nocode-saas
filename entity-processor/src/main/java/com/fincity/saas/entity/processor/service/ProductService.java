package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO> {

    private static final String PRODUCT_CACHE = "product";

    private ProductTemplateService productTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }

    @Override
    protected Mono<Product> checkEntity(Product product, ProcessorAccess access) {

        if (product.getId() == null && product.getName().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.NAME_MISSING);

        return this.existsByName(access.getAppCode(), access.getClientCode(), product.getName())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                product.getName(),
                                getEntityName());

                    if (product.getProductTemplateId() != null)
                        return productTemplateService
                                .readByIdInternal(product.getProductTemplateId())
                                .thenReturn(product);

                    return Mono.just(product);
                });
    }

    @Override
    protected Mono<Product> updatableEntity(Product entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setProductTemplateId(entity.getProductTemplateId());

            return Mono.just(existing);
        });
    }

    public Mono<Product> create(ProductRequest productRequest) {

        if (productRequest.getProductTemplateId() == null
                || productRequest.getProductTemplateId().isNull()) return super.create(Product.of(productRequest));

        return FlatMapUtil.flatMapMono(
                () -> super.create(Product.of(productRequest)),
                created -> productTemplateService.readIdentityWithAccess(productRequest.getProductTemplateId()),
                (created, productTemplate) -> {
                    created.setProductTemplateId(productTemplate.getId());
                    return super.updateInternal(created);
                });
    }

    private Mono<Boolean> existsByName(String appCode, String clientCode, String productName) {
        return this.dao.existsByName(appCode, clientCode, productName);
    }

    public Mono<ProductTemplate> setProductTemplate(Identity productId, ProductTemplate productTemplate) {
        return FlatMapUtil.flatMapMono(() -> super.readIdentityWithAccess(productId), product -> {
            product.setProductTemplateId(productTemplate.getId());
            return super.updateInternal(product).map(updated -> productTemplate);
        });
    }
}
