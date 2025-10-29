package com.fincity.saas.commons.jooq.flow.service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import java.io.Serializable;
import org.jooq.UpdatableRecord;
import reactor.core.publisher.Mono;

public abstract class AbstractFlowDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowDTO<I, I>,
                O extends AbstractFlowDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {

    protected abstract <
                    R0 extends UpdatableRecord<R0>,
                    I0 extends Serializable,
                    D0 extends FlowSchema<I0, I0>,
                    O0 extends FlowSchemaDAO<R0, I0, D0>,
                    S0 extends FlowSchemaService<R0, I0, D0, O0>>
            S0 getFlowSchemaService();

    @Override
    public Mono<D> create(D entity) {
        return this.validateSchema(entity).flatMap(vEntity -> super.create(entity));
    }

    private Mono<D> validateSchema(D entity) {
        return FlatMapUtil.flatMapMono(() -> this.getEntitySchema(entity), schema -> ReactiveSchemaValidator.validate(
                                null, schema, null, entity.toJsonElement())
                        .map(validated -> entity))
                .switchIfEmpty(Mono.just(entity));
    }

    private Mono<Schema> getEntitySchema(D entity) {
        return this.getFlowSchemaService() != null
                ? this.getFlowSchemaService()
                        .getSchema(
                                entity.getTableName(),
                                entity.getFlowSchemaEntityField(),
                                entity.getFlowSchemaEntityId())
                : Mono.just(entity.getSchema());
    }
}
