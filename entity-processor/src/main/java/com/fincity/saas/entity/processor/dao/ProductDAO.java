package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductDAO extends BaseProcessorDAO<EntityProcessorProductsRecord, Product> {

    protected ProductDAO() {
        super(Product.class, ENTITY_PROCESSOR_PRODUCTS, ENTITY_PROCESSOR_PRODUCTS.ID);
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {

        if (!access.isOutsideUser()) return super.processorAccessCondition(condition, access);

        return super.processorAccessCondition(condition, access)
                .flatMap(con -> Mono.just(ComplexCondition.and(con, getPartnerCondition())));
    }

    private AbstractCondition getPartnerCondition() {
        return FilterCondition.make(Product.Fields.forPartner, Boolean.TRUE);
    }
}
