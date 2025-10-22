package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductWalkInForms.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductWalkInFormDAO extends BaseUpdatableDAO<EntityProcessorProductWalkInFormsRecord, ProductWalkInForm> {

    public ProductWalkInFormDAO() {
        super(
                ProductWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS.ID);
    }

    public Mono<ProductWalkInForm> findByAppClientAndProductId(ProcessorAccess access, ULong productId) {

        return FlatMapUtil.flatMapMono(
                () -> super.processorAccessCondition(
                        FilterCondition.make(ProductWalkInForm.Fields.productId, productId), access),
                super::filter,
                (abstractCondition, jooqCondition) -> Mono.from(
                                dslContext.selectFrom(this.table).where(jooqCondition.and(super.isActiveTrue())))
                        .map(record -> record.into(ProductWalkInForm.class)));
    }
}
