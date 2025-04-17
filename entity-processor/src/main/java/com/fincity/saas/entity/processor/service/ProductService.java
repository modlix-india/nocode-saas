package com.fincity.saas.entity.processor.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;

@Service
public class ProductService extends BaseProcessorService<EntityProcessorProductsRecord, Product, ProductDAO> {

	private static final String PRODUCT_CACHE = "product";

	@Override
	protected String getCacheName() {
		return PRODUCT_CACHE;
	}
}
