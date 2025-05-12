package com.fincity.saas.entity.processor.service;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO>
        implements IValueTemplateService {

    private static final String PRODUCT_CACHE = "product";

    @Override
    protected String getCacheName() {
        return PRODUCT_CACHE;
    }

    @Override
    protected Mono<Product> checkEntity(Product entity, Tuple3<String, String, ULong> accessInfo) {
        return Mono.just(entity);
    }

    public Mono<Product> create(ProductRequest productRequest) {
        return super.create(Product.of(productRequest));
    }

    public Mono<Product> readWithAccess(Identity identity) {
        return super.hasAccess().flatMap(hasAccess -> {
            if (!hasAccess.getT2())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        ProcessorMessageResourceService.PRODUCT_FORBIDDEN_ACCESS);

            return this.readIdentity(identity);
        });
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT;
    }

    @Override
    public Mono<ValueTemplate> updateValueTemplate(Identity identity, ValueTemplate valueTemplate) {
        return FlatMapUtil.flatMapMono(() -> super.readIdentity(identity), product -> {
            product.setValueTemplateId(valueTemplate.getId());
            return super.updateInternal(product).map(updated -> valueTemplate);
        });
    }
}
