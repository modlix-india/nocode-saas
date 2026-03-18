package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSourceConfigs.ENTITY_PROCESSOR_SOURCE_CONFIGS;

import com.fincity.saas.entity.processor.dto.SourceConfig;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSourceConfigs;
import java.util.Collection;
import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SourceConfigDAO {

    private static final EntityProcessorSourceConfigs T = ENTITY_PROCESSOR_SOURCE_CONFIGS;

    private final DSLContext dslContext;

    public SourceConfigDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Flux<SourceConfig> findAllByAppAndClient(String appCode, String clientCode) {
        return Flux.from(
                this.dslContext.selectFrom(T)
                        .where(T.APP_CODE.eq(appCode))
                        .and(T.CLIENT_CODE.eq(clientCode))
                        .orderBy(T.DISPLAY_ORDER.asc()))
                .map(rec -> rec.into(SourceConfig.class));
    }

    public Flux<SourceConfig> findActiveByAppAndClient(String appCode, String clientCode) {
        return Flux.from(
                this.dslContext.selectFrom(T)
                        .where(T.APP_CODE.eq(appCode))
                        .and(T.CLIENT_CODE.eq(clientCode))
                        .and(T.IS_ACTIVE.eq(true))
                        .orderBy(T.DISPLAY_ORDER.asc()))
                .map(rec -> rec.into(SourceConfig.class));
    }

    public Mono<Boolean> existsForClient(String appCode, String clientCode) {
        return Mono.from(
                this.dslContext.selectCount().from(T)
                        .where(T.APP_CODE.eq(appCode))
                        .and(T.CLIENT_CODE.eq(clientCode)))
                .map(rec -> rec.value1() > 0);
    }

    public Mono<Void> deleteAllByAppAndClient(String appCode, String clientCode) {
        return Mono.from(
                this.dslContext.deleteFrom(T)
                        .where(T.APP_CODE.eq(appCode))
                        .and(T.CLIENT_CODE.eq(clientCode))
                        .and(T.PARENT_ID.isNotNull()))
                .then(Mono.from(
                        this.dslContext.deleteFrom(T)
                                .where(T.APP_CODE.eq(appCode))
                                .and(T.CLIENT_CODE.eq(clientCode))))
                .then();
    }

    public Mono<SourceConfig> insert(SourceConfig config) {
        return Mono.from(
                this.dslContext.insertInto(T)
                        .set(T.APP_CODE, config.getAppCode())
                        .set(T.CLIENT_CODE, config.getClientCode())
                        .set(T.NAME, config.getName())
                        .set(T.PARENT_ID, config.getParentId())
                        .set(T.DISPLAY_ORDER, config.getDisplayOrder())
                        .set(T.IS_CALL_SOURCE, config.isCallSource())
                        .set(T.IS_DEFAULT_SOURCE, config.isDefaultSource())
                        .set(T.IS_ACTIVE, config.isActive())
                        .set(T.CREATED_BY, config.getCreatedBy())
                        .returningResult(T.ID))
                .map(rec -> {
                    config.setId(rec.get(T.ID));
                    return config;
                });
    }

    public Mono<SourceConfig> update(SourceConfig config) {
        return Mono.from(
                this.dslContext.update(T)
                        .set(T.NAME, config.getName())
                        .set(T.PARENT_ID, config.getParentId())
                        .set(T.DISPLAY_ORDER, config.getDisplayOrder())
                        .set(T.IS_CALL_SOURCE, config.isCallSource())
                        .set(T.IS_DEFAULT_SOURCE, config.isDefaultSource())
                        .set(T.IS_ACTIVE, config.isActive())
                        .set(T.UPDATED_BY, config.getUpdatedBy())
                        .where(T.ID.eq(config.getId())))
                .thenReturn(config);
    }

    public Mono<Void> deleteByAppAndClientExcludingIds(String appCode, String clientCode,
            Collection<ULong> keepIds) {
        if (keepIds == null || keepIds.isEmpty())
            return this.deleteAllByAppAndClient(appCode, clientCode);

        return Mono.from(
                this.dslContext.deleteFrom(T)
                        .where(T.APP_CODE.eq(appCode))
                        .and(T.CLIENT_CODE.eq(clientCode))
                        .and(T.ID.notIn(keepIds)))
                .then();
    }
}
