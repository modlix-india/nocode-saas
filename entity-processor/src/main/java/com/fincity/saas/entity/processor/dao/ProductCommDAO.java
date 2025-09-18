package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_COMMS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductCommDAO extends BaseProcessorDAO<EntityProcessorProductCommsRecord, ProductComm> {

    protected ProductCommDAO() {
        super(ProductComm.class, ENTITY_PROCESSOR_PRODUCT_COMMS, ENTITY_PROCESSOR_PRODUCT_COMMS.ID);
    }

    public Mono<ProductComm> getDefaultProductComm(
            ProcessorAccess access, ULong productId, String connectionName, ConnectionType connectionType) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommDefaultCondition(access, productId, connectionName, connectionType),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access,
            ULong productId,
            String connectionName,
            ConnectionType connectionType,
            String source,
            String subSource) {

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommCondition(
                        access, productId, connectionName, connectionType, source, subSource),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    private Mono<AbstractCondition> getProductCommDefaultCondition(
            ProcessorAccess access, ULong productId, String connectionName, ConnectionType connectionType) {

        AbstractCondition defaultCondition = new FilterCondition()
                .setField(ProductComm.Fields.isDefault)
                .setMatchOperator(FilterConditionOperator.IS_TRUE);

        return this.getProductCommCondition(access, productId, connectionName, connectionType, defaultCondition);
    }

    private Mono<AbstractCondition> getProductCommCondition(
            ProcessorAccess access,
            ULong productId,
            String connectionName,
            ConnectionType connectionType,
            String source,
            String subSource) {

        AbstractCondition sourceCondition = FilterCondition.make(ProductComm.Fields.source, source);

        AbstractCondition sourceSubSourceCondition = subSource != null && !subSource.isEmpty()
                ? ComplexCondition.and(sourceCondition, FilterCondition.make(ProductComm.Fields.subSource, subSource))
                : sourceCondition;

        return this.getProductCommCondition(
                access, productId, connectionName, connectionType, sourceSubSourceCondition);
    }

    private Mono<AbstractCondition> getProductCommCondition(
            ProcessorAccess access,
            ULong productId,
            String connectionName,
            ConnectionType connectionType,
            AbstractCondition sourceCondition) {

        AbstractCondition productCondition = FilterCondition.make(ProductComm.Fields.productId, productId);

        AbstractCondition connectionCondition = FilterCondition.make(ProductComm.Fields.connectionName, connectionName);

        AbstractCondition connectionTypeCondition =
                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name());

        return super.processorAccessCondition(
                ComplexCondition.and(productCondition, connectionCondition, connectionTypeCondition, sourceCondition),
                access);
    }
}
