package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_MESSAGE_CONFIGS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductMessageConfigsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

@Component
public class ProductMessageConfigDAO
        extends BaseUpdatableDAO<EntityProcessorProductMessageConfigsRecord, ProductMessageConfig> {

    protected ProductMessageConfigDAO() {
        super(
                ProductMessageConfig.class,
                ENTITY_PROCESSOR_PRODUCT_MESSAGE_CONFIGS,
                ENTITY_PROCESSOR_PRODUCT_MESSAGE_CONFIGS.ID);
    }

    public Mono<List<ProductMessageConfig>> getConfigs(
            ProcessorAccess access,
            ULong productId,
            ULong stageId,
            ULong statusId,
            MessageChannelType channel) {

        return FlatMapUtil.flatMapMono(
                () -> this.getConfigCondition(access, productId, stageId, statusId, channel),
                super::filter,
                (pCondition, jCondition) -> Flux.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_PRODUCT_MESSAGE_CONFIGS)
                                .where(jCondition.and(super.isActiveTrue()))
                                .orderBy(ENTITY_PROCESSOR_PRODUCT_MESSAGE_CONFIGS.ID.asc()))
                        .map(rec -> rec.into(ProductMessageConfig.class))
                        .collectList());
    }

    private Mono<AbstractCondition> getConfigCondition(
            ProcessorAccess access,
            ULong productId,
            ULong stageId,
            ULong statusId,
            MessageChannelType channel) {

        AbstractCondition condition = ComplexCondition.and(
                FilterCondition.make(ProductMessageConfig.Fields.productId, productId),
                FilterCondition.make(ProductMessageConfig.Fields.stageId, stageId),
                FilterCondition.make(ProductMessageConfig.Fields.statusId, statusId),
                FilterCondition.make(ProductMessageConfig.Fields.channel, channel.getLiteral()));

        return super.processorAccessCondition(condition, access);
    }
}

