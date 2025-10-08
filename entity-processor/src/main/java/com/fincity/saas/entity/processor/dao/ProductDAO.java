package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
        return FilterCondition.make(Product.Fields.forPartner, 1);
    }

    public Mono<List<Product>> getAllProducts(ProcessorAccess access, List<ULong> productIds) {
        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(
                        productIds != null
                                ? new FilterCondition()
                                        .setField(AbstractDTO.Fields.id)
                                        .setOperator(FilterConditionOperator.IN)
                                        .setMultiValue(productIds)
                                : null,
                        access),
                super::filter,
                (condition, jCondition) -> Flux.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(Product.class))
                        .collectList());
    }
}
