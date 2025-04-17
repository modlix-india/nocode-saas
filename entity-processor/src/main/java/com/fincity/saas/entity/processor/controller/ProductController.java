package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.service.ProductService;

public class ProductController
        extends BaseProcessorController<EntityProcessorProductsRecord, Product, ProductDAO, ProductService> {}
