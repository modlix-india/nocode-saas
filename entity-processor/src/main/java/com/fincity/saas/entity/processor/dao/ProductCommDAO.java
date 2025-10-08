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
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductCommDAO extends BaseProcessorDAO<EntityProcessorProductCommsRecord, ProductComm> {

    protected ProductCommDAO() {
        super(ProductComm.class, ENTITY_PROCESSOR_PRODUCT_COMMS, ENTITY_PROCESSOR_PRODUCT_COMMS.ID);
    }

    public Mono<ProductComm> getDefaultAppProductComm(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType) {

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommAppDefaultCondition(access, connectionType, connectionSubType),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getDefaultProductComm(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {

        if (productId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommDefaultCondition(access, productId, connectionType, connectionSubType),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            Integer dialCode,
            String phoneNumber) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommPhoneCondition(
                        access, connectionType, connectionSubType, dialCode, phoneNumber),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType, String email) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommEmailCondition(access, connectionType, connectionSubType, email),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    public Mono<ProductComm> getProductComm(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String source,
            String subSource) {

        return FlatMapUtil.flatMapMono(
                () -> this.getProductCommCondition(
                        access, productId, connectionType, connectionSubType, source, subSource),
                super::filter,
                (pCondition, condition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_COMMS)
                                .where(condition))
                        .map(rec -> rec.into(ProductComm.class)));
    }

    private Mono<AbstractCondition> getProductCommPhoneCondition(
            ProcessorAccess access,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            Integer dialCode,
            String phoneNumber) {
        AbstractCondition phoneCondition = ComplexCondition.and(
                FilterCondition.make(ProductComm.Fields.dialCode, dialCode),
                FilterCondition.make(ProductComm.Fields.phoneNumber, phoneNumber));

        AbstractCondition connectionTypeCondition = ComplexCondition.and(
                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name()),
                FilterCondition.make(ProductComm.Fields.connectionSubType, connectionSubType.name()));

        return super.processorAccessCondition(ComplexCondition.and(connectionTypeCondition, phoneCondition), access);
    }

    private Mono<AbstractCondition> getProductCommEmailCondition(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType, String email) {
        AbstractCondition emailCondition = FilterCondition.make(ProductComm.Fields.email, email);

        AbstractCondition connectionTypeCondition = ComplexCondition.and(
                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name()),
                FilterCondition.make(ProductComm.Fields.connectionSubType, connectionSubType.name()));

        return super.processorAccessCondition(ComplexCondition.and(connectionTypeCondition, emailCondition), access);
    }

    private Mono<AbstractCondition> getProductCommDefaultCondition(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType) {

        AbstractCondition defaultCondition = new FilterCondition()
                .setField(ProductComm.Fields.isDefault)
                .setOperator(FilterConditionOperator.IS_TRUE);

        return this.getProductCommCondition(access, productId, connectionType, connectionSubType, defaultCondition);
    }

    private Mono<AbstractCondition> getProductCommAppDefaultCondition(
            ProcessorAccess access, ConnectionType connectionType, ConnectionSubType connectionSubType) {

        AbstractCondition defaultCondition = ComplexCondition.and(
                new FilterCondition()
                        .setField(ProductComm.Fields.isDefault)
                        .setOperator(FilterConditionOperator.IS_TRUE),
                new FilterCondition()
                        .setField(ProductComm.Fields.productId)
                        .setOperator(FilterConditionOperator.IS_NULL));

        return this.getProductCommCondition(access, null, connectionType, connectionSubType, defaultCondition);
    }

    private Mono<AbstractCondition> getProductCommCondition(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String source,
            String subSource) {

        AbstractCondition sourceCondition = FilterCondition.make(ProductComm.Fields.source, source);

        AbstractCondition sourceSubSourceCondition = subSource != null && !subSource.isEmpty()
                ? ComplexCondition.and(sourceCondition, FilterCondition.make(ProductComm.Fields.subSource, subSource))
                : sourceCondition;

        return this.getProductCommCondition(
                access, productId, connectionType, connectionSubType, sourceSubSourceCondition);
    }

    private Mono<AbstractCondition> getProductCommCondition(
            ProcessorAccess access,
            ULong productId,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            AbstractCondition sourceCondition) {

        AbstractCondition connectionTypeCondition = ComplexCondition.and(
                FilterCondition.make(ProductComm.Fields.connectionType, connectionType.name()),
                FilterCondition.make(ProductComm.Fields.connectionSubType, connectionSubType.name()));

        if (productId != null) {

            AbstractCondition productCondition = FilterCondition.make(ProductComm.Fields.productId, productId);

            return super.processorAccessCondition(
                    ComplexCondition.and(productCondition, connectionTypeCondition, sourceCondition), access);
        }

        return super.processorAccessCondition(ComplexCondition.and(connectionTypeCondition, sourceCondition), access);
    }
}
