package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductDAO extends BaseProcessorDAO<EntityProcessorProductsRecord, Product> {

    protected ProductDAO() {
        super(Product.class, ENTITY_PROCESSOR_PRODUCTS, ENTITY_PROCESSOR_PRODUCTS.ID);
    }

    public Mono<Boolean> existsByName(String appCode, String clientCode, String productName) {

        if (StringUtil.safeIsBlank(productName)) return Mono.just(Boolean.FALSE);

        List<Condition> baseConditions = new ArrayList<>();
        baseConditions.add(super.appCodeField.eq(appCode));
        baseConditions.add(super.clientCodeField.eq(clientCode));
        baseConditions.add(super.nameField.eq(productName));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(baseConditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
