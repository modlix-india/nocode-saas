package com.fincity.saas.commons.jooq.flow.service.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.commons.service.CacheService;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
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

    private static final String SCHEMA_CACHE = "schema";

    protected CacheService cacheService;

    private ObjectMapper mapper;

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    private void setObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    protected String getSchemaCache() {
        return SCHEMA_CACHE;
    }

    protected abstract String getServerNameSpace();

    protected abstract String getDbSchemaName();

    protected abstract Mono<Schema> getSchema(String dbTableName);

    protected abstract Mono<Schema> getEntityIdSchema(String dbTableName, String dbEntityPkFieldName, I dbEntityId);

    public Mono<Schema> getSchema(String dbTableName, String dbEntityPkFieldName, I dbEntityId) {

        if (dbTableName == null || dbTableName.isEmpty()) return Mono.empty();

        if (dbEntityPkFieldName != null && dbEntityId != null)
            return this.getEntityIdSchema(dbTableName, dbEntityPkFieldName, dbEntityId)
                    .switchIfEmpty(this.getSchema(dbTableName));

        return this.getSchema(dbTableName);
    }

    protected String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    protected String getCacheKey(Object... entityNames) {
        return String.join(
                ":",
                Stream.of(entityNames)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toArray(String[]::new));
    }

    protected Mono<Boolean> evictCache(D entity) {
        return Mono.just(Boolean.TRUE);
    }

    @Override
    public Mono<D> update(I key, Map<String, Object> fields) {
        return super.update(key, fields)
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    public Mono<D> update(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return this.dao.readById(entity.getId()).flatMap(existing -> {
            existing.setSchema(entity.getSchema());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return super.create(this.updateEntity(entity));
    }

    private D updateEntity(D entity) {

        entity.setDbSchemaName(this.getDbSchemaName());
        entity.setDbTableName(entity.getDbTableName());

        if (!entity.getDbTableName().startsWith(entity.getDbSchemaName()))
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    StringFormatter.format(
                            AbstractMessageService.INVALID_TABLE_NAME,
                            entity.getDbTableName(),
                            entity.getDbSchemaName()));

        entity.setSchema(this.toFieldMap(entity));

        return entity;
    }

    private Map<String, Object> toFieldMap(D entity) {

        Schema schema = DbSchema.setEntityNameAndNamespace(
                this.toSchema(entity.getSchema()), entity.getClass(), this.getServerNameSpace());

        return this.schemaToMap(schema);
    }

    protected Schema toSchema(D entity) {
        return this.toSchema(entity.getSchema());
    }

    protected Schema toSchema(Map<String, Object> map) {
        return mapper.convertValue(map, Schema.class);
    }

    private Map<String, Object> schemaToMap(Schema schema) {
        return mapper.convertValue(schema, new TypeReference<>() {});
    }
}
