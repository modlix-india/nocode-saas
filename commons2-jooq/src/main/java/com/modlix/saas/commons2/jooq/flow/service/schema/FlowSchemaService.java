package com.modlix.saas.commons2.jooq.flow.service.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.namespaces.Namespaces;
import com.modlix.saas.commons2.jooq.flow.dao.schema.FlowSchemaDAO;
import com.modlix.saas.commons2.jooq.flow.dto.schema.FlowSchema;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import com.modlix.saas.commons2.util.Case;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jooq.UpdatableRecord;

public abstract class FlowSchemaService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends FlowSchema<I, I>,
                O extends FlowSchemaDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

    private static final String FLOW_SCHEMA_NAMESPACE = Namespaces.SYSTEM + ".FlowSchema";
    private static final String FLOW_FIELD_NAMESPACE = FLOW_SCHEMA_NAMESPACE + ".Field";

    private static final UnaryOperator<String> dbNameConverter = Case.SNAKE.getConverter();
    private static final UnaryOperator<String> schemaNameConverter = Case.PASCAL.getConverter();
    private static final UnaryOperator<String> fieldNameConverter = Case.SCREAMING_SNAKE_CASE.getConverter();

    private final ObjectMapper mapper;

    protected FlowSchemaService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected D updatableEntity(D entity) {

        D existing = this.dao.readById(entity.getId());

        existing.setFieldSchema(entity.getFieldSchema());

        return existing;
    }

    @Override
    public D create(D entity) {
        return super.create(this.updateEntity(entity));
    }

    @SuppressWarnings("unchecked")
    private D updateEntity(D entity) {

        entity.setDbSchema(dbNameConverter.apply(entity.getDbSchema()));
        entity.setDbTableName(dbNameConverter.apply(entity.getDbTableName()));

        return (D) entity.setFieldSchema(
                this.toFieldMap(entity.getDbSchema(), entity.getDbTableName(), entity.getFieldSchema()));
    }

    public D addField(I id, String fieldName, Map<String, Object> schema) {

        D existing = this.dao.readById(id);

        Map<String, Map<String, Object>> fieldSchema =
                existing.getFieldSchema() != null ? existing.getFieldSchema() : new LinkedHashMap<>();

        Map.Entry<String, Map<String, Object>> entry =
                this.updateSchemaEntry(existing.getDbSchema(), existing.getDbTableName(), Map.entry(fieldName, schema));

        fieldSchema.put(entry.getKey(), entry.getValue());

        existing.setFieldSchema(fieldSchema);
        return super.update(existing);
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

    private Schema toSchema(Map<String, Object> map) {
        return mapper.convertValue(map, Schema.class);
    }

    private Map<String, Object> schemaToMap(Schema schema) {
        return mapper.convertValue(schema, new TypeReference<>() {});
    }
}
