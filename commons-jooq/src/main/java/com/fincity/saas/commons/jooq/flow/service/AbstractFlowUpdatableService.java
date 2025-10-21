package com.fincity.saas.commons.jooq.flow.service;

import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.Serializable;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class AbstractFlowUpdatableService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowUpdatableDTO<I, I>,
                O extends AbstractFlowUpdatableDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

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
    public Mono<D> update(D entity) {
        return this.validateSchema(entity)
                .flatMap(vEntity -> super.update(entity))
                .switchIfEmpty(super.update(entity));
    }

    @Override
    public Mono<D> update(I key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                () -> super.read(key),
                entity -> this.validateSchema(entity, fields),
                (entity, vFields) -> super.update(key, vFields));
    }

    @Override
    public Mono<D> create(D entity) {
        return this.validateSchema(entity)
                .flatMap(vEntity -> super.create(entity))
                .switchIfEmpty(super.create(entity));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> validateSchema(D entity, Map<String, Object> entityMap) {

        if (!entityMap.containsKey(AbstractFlowUpdatableDTO.Fields.fields)) return Mono.just(entityMap);

        return FlatMapUtil.flatMapMono(
                        () -> this.getFlowSchemaService().getSchema(entity.getDbTableName(), entity.getId()),
                        schema -> ReactiveSchemaValidator.validate(
                                null, schema, null, this.toJsonElement((Map<String, Object>)
                                        entityMap.get(AbstractFlowUpdatableDTO.Fields.fields))),
                        (schema, jsonElement) -> this.toMap(jsonElement).map(vMap -> {
                            entityMap.put(AbstractFlowUpdatableDTO.Fields.fields, vMap);
                            return entityMap;
                        }))
                .switchIfEmpty(Mono.just(entityMap));
    }

    @SuppressWarnings("unchecked")
    private Mono<D> validateSchema(D entity) {

        return FlatMapUtil.flatMapMono(
                        () -> this.getFlowSchemaService().getSchema(entity.getDbTableName(), entity.getId()),
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
