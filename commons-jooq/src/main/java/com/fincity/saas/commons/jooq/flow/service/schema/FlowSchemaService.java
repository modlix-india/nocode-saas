package com.fincity.saas.commons.jooq.flow.service.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.namespaces.Namespaces;
import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.Case;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class FlowSchemaService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends FlowSchema<I, I>,
                O extends FlowSchemaDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

    private static final String FLOW_SCHEMA_CACHE_NAME = "flowSchema";
    private static final String SCHEMA_CACHE_NAME = "schema";
    private static final String FLOW_SCHEMA_NAMESPACE = Namespaces.SYSTEM + ".FlowSchema";
    private static final String FLOW_FIELD_NAMESPACE = FLOW_SCHEMA_NAMESPACE + ".Field";
    private static final UnaryOperator<String> dbNameConverter = Case.SNAKE.getConverter();
    private static final UnaryOperator<String> schemaNameConverter = Case.PASCAL.getConverter();
    private static final UnaryOperator<String> fieldNameConverter = Case.SCREAMING_SNAKE_CASE.getConverter();

    private CacheService cacheService;

    private ObjectMapper mapper;

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    private void setObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    protected abstract String getDbSchemaName();

    protected abstract Mono<Schema> getSchema(String dbTableName);

    protected abstract Mono<Schema> getIdSchema(String dbTableName, I dbId);

    public Mono<Schema> getSchema(String dbTableName, I dbId) {

        if (dbTableName == null || dbTableName.isEmpty()) return Mono.empty();

        if (dbId == null) return this.getSchema(dbTableName);

        return this.getIdSchema(dbTableName, dbId).switchIfEmpty(this.getSchema(dbTableName));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return this.dao.readById(entity.getId()).flatMap(existing -> {
            existing.setFieldSchema(entity.getFieldSchema());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return super.create(this.updateEntity(entity));
    }

    public Mono<D> addField(I id, String fieldName, Map<String, Object> schema) {
        return this.dao.readById(id).flatMap(existing -> {
            Map<String, Map<String, Object>> fieldSchema =
                    existing.getFieldSchema() != null ? existing.getFieldSchema() : new LinkedHashMap<>();

            Map.Entry<String, Map<String, Object>> entry = this.updateSchemaEntry(
                    existing.getDbSchema(), existing.getDbTableName(), Map.entry(fieldName, schema));

            fieldSchema.put(entry.getKey(), entry.getValue());

            existing.setFieldSchema(fieldSchema);
            return super.update(existing);
        });
    }

    @SuppressWarnings("unchecked")
    private D updateEntity(D entity) {

        entity.setDbSchema(this.getDbSchemaName());
        entity.setDbTableName(dbNameConverter.apply(entity.getDbTableName()));

        if (!entity.getDbTableName().startsWith(entity.getDbSchema()))
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    StringFormatter.format(
                            AbstractMessageService.INVALID_TABLE_NAME, entity.getDbTableName(), entity.getDbSchema()));

        return (D) entity.setFieldSchema(
                this.toFieldMap(entity.getDbSchema(), entity.getDbTableName(), entity.getFieldSchema()));
    }

    private Map<String, Map<String, Object>> toFieldMap(
            String dbSchema, String dbTableName, Map<String, Map<String, Object>> fieldMap) {

        Map<String, Map<String, Object>> fieldSchema = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> field : fieldMap.entrySet()) {
            Map.Entry<String, Map<String, Object>> entry = this.updateSchemaEntry(dbSchema, dbTableName, field);
            fieldSchema.put(entry.getKey(), entry.getValue());
        }

        return fieldSchema;
    }

    private Map.Entry<String, Map<String, Object>> updateSchemaEntry(
            String dbSchema, String dbTableName, Map.Entry<String, Map<String, Object>> entry) {

        String key = fieldNameConverter.apply(entry.getKey());

        Schema schema = this.toSchema(entry.getValue())
                .setName(schemaNameConverter.apply(entry.getKey()))
                .setNamespace(this.getNamespace(dbSchema, dbTableName));

        return Map.entry(key, this.schemaToMap(schema));
    }

    private String getNamespace(String dbSchema, String dbTableName) {
        return FLOW_FIELD_NAMESPACE + "." + schemaNameConverter.apply(dbSchema) + "."
                + schemaNameConverter.apply(dbTableName);
    }

    protected Schema toSchema(Map<String, Object> map) {
        return mapper.convertValue(map, Schema.class);
    }

    private Map<String, Object> schemaToMap(Schema schema) {
        return mapper.convertValue(schema, new TypeReference<>() {});
    }
}
