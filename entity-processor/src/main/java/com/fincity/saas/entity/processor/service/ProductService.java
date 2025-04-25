package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO> {

    private static final String PRODUCT_CACHE = "product";

    @Override
    protected String getCacheName() {
        return PRODUCT_CACHE;
    }

    @Override
    protected Mono<Product> checkEntity(Product entity, Tuple3<String, String, ULong> accessInfo) {
        return Mono.just(entity);
    }
}
