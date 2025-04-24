package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductDAO extends BaseProcessorDAO<EntityProcessorProductsRecord, Product> {

    protected ProductDAO() {
        super(Product.class, ENTITY_PROCESSOR_PRODUCTS, ENTITY_PROCESSOR_PRODUCTS.ID);
    }
}
