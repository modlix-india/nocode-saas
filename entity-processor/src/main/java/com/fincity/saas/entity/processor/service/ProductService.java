package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

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
    protected Mono<Product> checkEntity(Product product, Tuple3<String, String, ULong> accessInfo) {

        if (product.getName().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.NAME_MISSING);

        return productTemplateService
                .readByIdInternal(product.getProductTemplateId())
                .map(valueTemplate -> product);
    }

    @Override
    protected Mono<Product> updatableEntity(Product entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setProductTemplateId(entity.getProductTemplateId());

            return Mono.just(existing);
        });
    }

    public Mono<Product> create(ProductRequest productRequest) {
        return super.create(Product.of(productRequest));
    }

    public Mono<Product> readWithAccess(Identity identity) {
        return super.hasAccess().flatMap(hasAccess -> {
            if (Boolean.FALSE.equals(hasAccess.getT2()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        ProcessorMessageResourceService.PRODUCT_FORBIDDEN_ACCESS,
                        identity.toString());

            return this.readIdentityInternal(identity);
        });
    }

    public Mono<ProductTemplate> setProductTemplate(Identity productId, ProductTemplate productTemplate) {
        return FlatMapUtil.flatMapMono(() -> super.readIdentity(productId), product -> {
            product.setProductTemplateId(productTemplate.getId());
            return super.updateInternal(product).map(updated -> productTemplate);
        });
    }
}
