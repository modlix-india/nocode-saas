package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSources.ENTITY_PROCESSOR_SOURCES;

import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import java.util.Collection;
import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SourceDAO {

    private final DSLContext dslContext;

    public SourceDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Flux<Source> findAllByAppAndClient(String appCode, String clientCode) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))
                        .orderBy(ENTITY_PROCESSOR_SOURCES.DISPLAY_ORDER.asc()))
                .map(SourceDAO::toDto);
    }

    public Flux<Source> findActiveByAppAndClient(String appCode, String clientCode) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_SOURCES.IS_ACTIVE.eq(true))
                        .orderBy(ENTITY_PROCESSOR_SOURCES.DISPLAY_ORDER.asc()))
                .map(SourceDAO::toDto);
    }

    public Mono<Source> insert(Source source) {
        return Mono.from(this.dslContext
                        .insertInto(ENTITY_PROCESSOR_SOURCES)
                        .set(ENTITY_PROCESSOR_SOURCES.APP_CODE, source.getAppCode())
                        .set(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE, source.getClientCode())
                        .set(ENTITY_PROCESSOR_SOURCES.NAME, source.getName())
                        .set(ENTITY_PROCESSOR_SOURCES.PARENT_ID, source.getParentId())
                        .set(ENTITY_PROCESSOR_SOURCES.DISPLAY_ORDER, source.getDisplayOrder())
                        .set(ENTITY_PROCESSOR_SOURCES.IS_ACTIVE, source.isActive())
                        .set(ENTITY_PROCESSOR_SOURCES.CREATED_BY, source.getCreatedBy())
                        .returningResult(ENTITY_PROCESSOR_SOURCES.ID))
                .map(rec -> {
                    source.setId(rec.get(ENTITY_PROCESSOR_SOURCES.ID));
                    return source;
                });
    }

    public Mono<Source> update(Source source) {
        return Mono.from(this.dslContext
                        .update(ENTITY_PROCESSOR_SOURCES)
                        .set(ENTITY_PROCESSOR_SOURCES.NAME, source.getName())
                        .set(ENTITY_PROCESSOR_SOURCES.PARENT_ID, source.getParentId())
                        .set(ENTITY_PROCESSOR_SOURCES.DISPLAY_ORDER, source.getDisplayOrder())
                        .set(ENTITY_PROCESSOR_SOURCES.IS_ACTIVE, source.isActive())
                        .set(ENTITY_PROCESSOR_SOURCES.UPDATED_BY, source.getUpdatedBy())
                        .where(ENTITY_PROCESSOR_SOURCES.ID.eq(source.getId()))
                        .and(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(source.getAppCode()))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(source.getClientCode())))
                .thenReturn(source);
    }

    public Mono<Void> deleteByAppAndClientExcludingIds(String appCode, String clientCode, Collection<ULong> keepIds) {

        if (keepIds == null || keepIds.isEmpty()) return this.deleteAllByAppAndClient(appCode, clientCode);

        return Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_SOURCES.PARENT_ID.isNotNull())
                        .and(ENTITY_PROCESSOR_SOURCES.ID.notIn(keepIds)))
                .then(Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_SOURCES.PARENT_ID.isNull())
                        .and(ENTITY_PROCESSOR_SOURCES.ID.notIn(keepIds))))
                .then();
    }

    private Mono<Void> deleteAllByAppAndClient(String appCode, String clientCode) {
        return Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_SOURCES.PARENT_ID.isNotNull()))
                .then(Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_SOURCES)
                        .where(ENTITY_PROCESSOR_SOURCES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_SOURCES.CLIENT_CODE.eq(clientCode))))
                .then();
    }

    private static Source toDto(EntityProcessorSourcesRecord rec) {
        Source source = new Source();
        source.setId(rec.getId());
        source.setAppCode(rec.getAppCode());
        source.setClientCode(rec.getClientCode());
        source.setName(rec.getName());
        source.setParentId(rec.getParentId());
        source.setDisplayOrder(rec.getDisplayOrder());
        source.setActive(Boolean.TRUE.equals(rec.getIsActive()));
        source.setCreatedBy(rec.getCreatedBy());
        source.setUpdatedBy(rec.getUpdatedBy());
        return source;
    }
}
