package com.fincity.saas.entity.collector.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.fincity.saas.entity.collector.jooq.tables.EntityCollectorLog.ENTITY_COLLECTOR_LOG;

@Repository
public class EntityCollectorLogDAO {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public EntityCollectorLogDAO(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public Mono<EntityCollectorLog> create(EntityCollectorLog log) {
        return Mono.from(dsl
                        .insertInto(ENTITY_COLLECTOR_LOG)
                        .set(toRecord(log))
                        .returning(ENTITY_COLLECTOR_LOG.ID))
                .flatMap(r -> getById(r.getId()));
    }

    public Mono<EntityCollectorLog> getById(ULong id) {
        return Mono.from(
                        dsl.selectFrom(ENTITY_COLLECTOR_LOG)
                                .where(ENTITY_COLLECTOR_LOG.ID.eq(id)))
                .map(r -> r.into(EntityCollectorLog.class));
    }

    public Mono<EntityCollectorLog> update(EntityCollectorLog log) {
        return Mono.from(
                        dsl.update(ENTITY_COLLECTOR_LOG)
                                .set(toRecord(log))
                                .where(ENTITY_COLLECTOR_LOG.ID.eq(log.getId()))
                                .returning(ENTITY_COLLECTOR_LOG.ID))
                .flatMap(r -> getById(r.getId()));
    }


    private EntityCollectorLogRecord toRecord(EntityCollectorLog log) {
        EntityCollectorLogRecord record = dsl.newRecord(ENTITY_COLLECTOR_LOG);
        record.from(log);
        return record;
    }
}
