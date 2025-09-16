package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_COMMS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductCommDAO extends BaseUpdatableDAO<EntityProcessorProductCommsRecord, ProductComm> {

    protected ProductCommDAO() {
        super(ProductComm.class, ENTITY_PROCESSOR_PRODUCT_COMMS, ENTITY_PROCESSOR_PRODUCT_COMMS.ID);
    }

    public Mono<Integer> unsetDefaultsForProduct(
            ProcessorAccess access, ULong productId, ConnectionType connectionType) {

        return FlatMapUtil.flatMapMono(
                () -> super.processorAccessCondition(
                        ComplexCondition.and(
                                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name()),
                                FilterCondition.make(ProductComm.Fields.productId, productId)),
                        access),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                        .update(ENTITY_PROCESSOR_PRODUCT_COMMS)
                        .set(ENTITY_PROCESSOR_PRODUCT_COMMS.IS_DEFAULT, DSL.inline((byte) 0))
                        .where(condition)));
    }

    public Mono<ProductComm> readByConnectionType(
            ProcessorAccess access, ULong productId, ConnectionType connectionType) {

        return FlatMapUtil.flatMapMono(
                () -> super.processorAccessCondition(
                        ComplexCondition.and(
                                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name()),
                                FilterCondition.make(ProductComm.Fields.productId, productId)),
                        access),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }
}
