package com.fincity.saas.commons.jooq.flow.service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
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
import java.util.stream.Collectors;
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

    protected <
                    R0 extends UpdatableRecord<R0>,
                    I0 extends Serializable,
                    D0 extends FlowSchema<I0, I0>,
                    O0 extends FlowSchemaDAO<R0, I0, D0>,
                    S0 extends FlowSchemaService<R0, I0, D0, O0>>
            S0 getFlowSchemaService() {
        return null;
    }

    @Autowired
    public void setGson(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Mono<D> update(D entity) {
        return this.validateSchema(entity).flatMap(vEntity -> super.update(entity));
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
        return this.validateSchema(entity).flatMap(vEntity -> super.create(entity));
    }

    private Mono<Map<String, Object>> validateSchema(D entity, Map<String, Object> entityMap) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getEntitySchema(entity).map(schema -> this.filterEntitySchema(schema, entityMap)),
                        schema -> ReactiveSchemaValidator.validate(null, schema, null, this.toJsonElement(entityMap))
                                .map(validated -> entityMap))
                .switchIfEmpty(Mono.just(entityMap));
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

    private Schema filterEntitySchema(Schema schema, Map<String, Object> entityMap) {
        Map<String, Schema> props = schema.getProperties();
        if (props == null || props.isEmpty() || entityMap == null || entityMap.isEmpty()) return schema;

        Map<String, Schema> filtered = props.entrySet().stream()
                .filter(entry -> !entityMap.containsKey(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        schema.setProperties(filtered);
        return schema;
    }

    private JsonElement toJsonElement(Map<String, Object> fields) {
        return gson.toJsonTree(fields);
    }
}
