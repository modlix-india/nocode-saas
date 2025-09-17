package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_COMMS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductCommDAO extends BaseProcessorDAO<EntityProcessorProductCommsRecord, ProductComm> {

    protected ProductCommDAO() {
        super(ProductComm.class, ENTITY_PROCESSOR_PRODUCT_COMMS, ENTITY_PROCESSOR_PRODUCT_COMMS.ID);
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access,
            ULong productId,
            Integer dialCode,
            String phoneNumber,
            String source,
            String subSource) {

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommPhoneCondition(access, productId, dialCode, phoneNumber, source, subSource),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access, ULong productId, String email, String source, String subSource) {

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommEmailCondition(access, productId, email, source, subSource),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
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

    private Mono<AbstractCondition> getProductCommEmailCondition(
            ProcessorAccess access, ULong productId, String email, String source, String subSource) {
        AbstractCondition emailCondition = FilterCondition.make(ProductComm.Fields.email, email);

        return this.getProductCommCondition(access, productId, source, subSource, emailCondition);
    }

    private Mono<AbstractCondition> getProductCommPhoneCondition(
            ProcessorAccess access,
            ULong productId,
            String dialCode,
            String phoneNumber,
            String source,
            String subSource) {

        AbstractCondition phoneCondition = ComplexCondition.and(
                FilterCondition.make(ProductComm.Fields.dialCode, dialCode),
                FilterCondition.make(ProductComm.Fields.phoneNumber, phoneNumber));

        return this.getProductCommCondition(access, productId, source, subSource, phoneCondition);
    }

    private Mono<AbstractCondition> getProductCommCondition(
            ProcessorAccess access,
            ULong productId,
            String source,
            String subSource,
            AbstractCondition connectionMediumCondition) {

        AbstractCondition productCondition = FilterCondition.make(ProductComm.Fields.productId, productId);

        AbstractCondition sourceCondition = FilterCondition.make(ProductComm.Fields.source, source);

        AbstractCondition sourceSubSourceCondition = subSource != null && !subSource.isEmpty()
                ? ComplexCondition.and(sourceCondition, FilterCondition.make(ProductComm.Fields.subSource, subSource))
                : sourceCondition;

        return super.processorAccessCondition(
                ComplexCondition.and(productCondition, connectionMediumCondition, sourceSubSourceCondition), access);
    }
}
