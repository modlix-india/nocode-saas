package com.fincity.saas.commons.jooq.flow.service;

import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.Serializable;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class AbstractFlowDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowDTO<I, I>,
                O extends AbstractFlowDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {

    private Gson gson;

    protected abstract <
                    R0 extends UpdatableRecord<R0>,
                    I0 extends Serializable,
                    D0 extends FlowSchema<I0, I0>,
                    O0 extends FlowSchemaDAO<R0, I0, D0>,
                    S0 extends FlowSchemaService<R0, I0, D0, O0>>
            S0 getFlowSchemaService();

    @Autowired
    public void setGson(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Mono<D> create(D entity) {
        return this.validateSchema(entity)
                .flatMap(vEntity -> super.create(entity))
                .switchIfEmpty(super.create(entity));
    }

    @SuppressWarnings("unchecked")
    private Mono<D> validateSchema(D entity) {

        return FlatMapUtil.flatMapMono(
                        () -> this.getFlowSchemaService().getSchema(entity.getTableName(), entity.getId()),
                        schema -> ReactiveSchemaValidator.validate(
                                null, schema, null, this.toJsonElement(entity.getFields())),
                        (schema, jsonElement) -> this.toMap(jsonElement).map(vMap -> (D) entity.setFields(vMap)))
                .switchIfEmpty(Mono.just(entity));
    }

    private JsonElement toJsonElement(Map<String, Object> fields) {
        return gson.toJsonTree(fields);
    }

    private Mono<Map<String, Object>> toMap(JsonElement jsonElement) {

        if (!jsonElement.isJsonObject()) return Mono.empty();

        return Mono.just(JsonUtil.toMap(jsonElement.getAsJsonObject()));
    }
}
