package com.fincity.saas.entity.processor.dao;

import com.fincity.saas.entity.processor.dto.SourceConfig;
import java.util.Collection;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SourceConfigDAO {

    private static final Table<Record> TABLE = DSL.table("entity_processor_source_configs");

    private static final Field<ULong> ID = DSL.field("ID", SQLDataType.BIGINTUNSIGNED);
    private static final Field<String> APP_CODE = DSL.field("APP_CODE", SQLDataType.CHAR(64));
    private static final Field<String> CLIENT_CODE = DSL.field("CLIENT_CODE", SQLDataType.CHAR(8));
    private static final Field<String> NAME = DSL.field("NAME", SQLDataType.VARCHAR(512));
    private static final Field<ULong> PARENT_ID = DSL.field("PARENT_ID", SQLDataType.BIGINTUNSIGNED);
    private static final Field<Integer> DISPLAY_ORDER = DSL.field("DISPLAY_ORDER", SQLDataType.INTEGER);
    private static final Field<Boolean> IS_CALL_SOURCE = DSL.field("IS_CALL_SOURCE", SQLDataType.BOOLEAN);
    private static final Field<Boolean> IS_DEFAULT_SOURCE = DSL.field("IS_DEFAULT_SOURCE", SQLDataType.BOOLEAN);
    private static final Field<Boolean> IS_ACTIVE = DSL.field("IS_ACTIVE", SQLDataType.BOOLEAN);
    private static final Field<ULong> CREATED_BY = DSL.field("CREATED_BY", SQLDataType.BIGINTUNSIGNED);
    private static final Field<ULong> UPDATED_BY = DSL.field("UPDATED_BY", SQLDataType.BIGINTUNSIGNED);

    private final DSLContext dslContext;

    public SourceConfigDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Flux<SourceConfig> findAllByAppAndClient(String appCode, String clientCode) {
        return Flux.from(
                this.dslContext.selectFrom(TABLE)
                        .where(APP_CODE.eq(appCode))
                        .and(CLIENT_CODE.eq(clientCode))
                        .orderBy(DISPLAY_ORDER.asc()))
                .map(this::toDto);
    }

    public Flux<SourceConfig> findActiveByAppAndClient(String appCode, String clientCode) {
        return Flux.from(
                this.dslContext.selectFrom(TABLE)
                        .where(APP_CODE.eq(appCode))
                        .and(CLIENT_CODE.eq(clientCode))
                        .and(IS_ACTIVE.eq(true))
                        .orderBy(DISPLAY_ORDER.asc()))
                .map(this::toDto);
    }

    public Mono<Boolean> existsForClient(String appCode, String clientCode) {
        return Mono.from(
                this.dslContext.selectCount().from(TABLE)
                        .where(APP_CODE.eq(appCode))
                        .and(CLIENT_CODE.eq(clientCode)))
                .map(rec -> rec.value1() > 0);
    }

    public Mono<Void> deleteAllByAppAndClient(String appCode, String clientCode) {
        return Mono.from(
                this.dslContext.deleteFrom(TABLE)
                        .where(APP_CODE.eq(appCode))
                        .and(CLIENT_CODE.eq(clientCode))
                        .and(PARENT_ID.isNotNull()))
                .then(Mono.from(
                        this.dslContext.deleteFrom(TABLE)
                                .where(APP_CODE.eq(appCode))
                                .and(CLIENT_CODE.eq(clientCode))))
                .then();
    }

    public Mono<SourceConfig> insert(SourceConfig config) {
        return Mono.from(
                this.dslContext.insertInto(TABLE)
                        .set(APP_CODE, config.getAppCode())
                        .set(CLIENT_CODE, config.getClientCode())
                        .set(NAME, config.getName())
                        .set(PARENT_ID, config.getParentId())
                        .set(DISPLAY_ORDER, config.getDisplayOrder())
                        .set(IS_CALL_SOURCE, config.isCallSource())
                        .set(IS_DEFAULT_SOURCE, config.isDefaultSource())
                        .set(IS_ACTIVE, config.isActive())
                        .set(CREATED_BY, config.getCreatedBy())
                        .returningResult(ID))
                .map(rec -> {
                    config.setId(rec.get(ID));
                    return config;
                });
    }

    public Mono<SourceConfig> update(SourceConfig config) {
        return Mono.from(
                this.dslContext.update(TABLE)
                        .set(NAME, config.getName())
                        .set(PARENT_ID, config.getParentId())
                        .set(DISPLAY_ORDER, config.getDisplayOrder())
                        .set(IS_CALL_SOURCE, config.isCallSource())
                        .set(IS_DEFAULT_SOURCE, config.isDefaultSource())
                        .set(IS_ACTIVE, config.isActive())
                        .set(UPDATED_BY, config.getUpdatedBy())
                        .where(ID.eq(config.getId())))
                .thenReturn(config);
    }

    public Mono<Void> deleteByAppAndClientExcludingIds(String appCode, String clientCode,
            java.util.Collection<ULong> keepIds) {
        if (keepIds == null || keepIds.isEmpty())
            return this.deleteAllByAppAndClient(appCode, clientCode);

        return Mono.from(
                this.dslContext.deleteFrom(TABLE)
                        .where(APP_CODE.eq(appCode))
                        .and(CLIENT_CODE.eq(clientCode))
                        .and(ID.notIn(keepIds)))
                .then();
    }

    private SourceConfig toDto(Record rec) {
        SourceConfig config = new SourceConfig();
        config.setId(rec.get(ID));
        config.setAppCode(rec.get(APP_CODE));
        config.setClientCode(rec.get(CLIENT_CODE));
        config.setName(rec.get(NAME));
        config.setParentId(rec.get(PARENT_ID));
        config.setDisplayOrder(rec.get(DISPLAY_ORDER));
        config.setCallSource(Boolean.TRUE.equals(rec.get(IS_CALL_SOURCE)));
        config.setDefaultSource(Boolean.TRUE.equals(rec.get(IS_DEFAULT_SOURCE)));
        config.setActive(Boolean.TRUE.equals(rec.get(IS_ACTIVE)));
        config.setCreatedBy(rec.get(CREATED_BY));
        config.setUpdatedBy(rec.get(UPDATED_BY));
        return config;
    }
}
