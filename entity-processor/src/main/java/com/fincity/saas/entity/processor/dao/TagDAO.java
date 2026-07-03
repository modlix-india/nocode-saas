package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTags.ENTITY_PROCESSOR_TAGS;

import com.fincity.saas.entity.processor.dto.Tag;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTagsRecord;
import java.util.Collection;
import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TagDAO {

    private final DSLContext dslContext;

    public TagDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Flux<Tag> findAllByAppAndClient(String appCode, String clientCode) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_TAGS)
                        .where(ENTITY_PROCESSOR_TAGS.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_TAGS.CLIENT_CODE.eq(clientCode))
                        .orderBy(ENTITY_PROCESSOR_TAGS.NAME.asc()))
                .map(TagDAO::toDto);
    }

    public Flux<Tag> findActiveByAppAndClient(String appCode, String clientCode) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_TAGS)
                        .where(ENTITY_PROCESSOR_TAGS.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_TAGS.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_TAGS.IS_ACTIVE.eq(true))
                        .orderBy(ENTITY_PROCESSOR_TAGS.NAME.asc()))
                .map(TagDAO::toDto);
    }

    public Mono<Tag> insert(Tag tag) {
        return Mono.from(this.dslContext
                        .insertInto(ENTITY_PROCESSOR_TAGS)
                        .set(ENTITY_PROCESSOR_TAGS.APP_CODE, tag.getAppCode())
                        .set(ENTITY_PROCESSOR_TAGS.CLIENT_CODE, tag.getClientCode())
                        .set(ENTITY_PROCESSOR_TAGS.NAME, tag.getName())
                        .set(ENTITY_PROCESSOR_TAGS.IS_ACTIVE, tag.isActive())
                        .set(ENTITY_PROCESSOR_TAGS.COLOR, tag.getColor())
                        .set(ENTITY_PROCESSOR_TAGS.ICON, tag.getIcon())
                        .set(ENTITY_PROCESSOR_TAGS.CREATED_BY, tag.getCreatedBy())
                        .returningResult(ENTITY_PROCESSOR_TAGS.ID))
                .map(rec -> {
                    tag.setId(rec.get(ENTITY_PROCESSOR_TAGS.ID));
                    return tag;
                });
    }

    public Mono<Tag> update(Tag tag) {
        return Mono.from(this.dslContext
                        .update(ENTITY_PROCESSOR_TAGS)
                        .set(ENTITY_PROCESSOR_TAGS.NAME, tag.getName())
                        .set(ENTITY_PROCESSOR_TAGS.IS_ACTIVE, tag.isActive())
                        .set(ENTITY_PROCESSOR_TAGS.COLOR, tag.getColor())
                        .set(ENTITY_PROCESSOR_TAGS.ICON, tag.getIcon())
                        .set(ENTITY_PROCESSOR_TAGS.UPDATED_BY, tag.getUpdatedBy())
                        .where(ENTITY_PROCESSOR_TAGS.ID.eq(tag.getId()))
                        .and(ENTITY_PROCESSOR_TAGS.APP_CODE.eq(tag.getAppCode()))
                        .and(ENTITY_PROCESSOR_TAGS.CLIENT_CODE.eq(tag.getClientCode())))
                .thenReturn(tag);
    }

    public Mono<Void> deleteByAppAndClientExcludingIds(String appCode, String clientCode, Collection<ULong> keepIds) {
        if (keepIds == null || keepIds.isEmpty()) return this.deleteAllByAppAndClient(appCode, clientCode);

        return Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_TAGS)
                        .where(ENTITY_PROCESSOR_TAGS.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_TAGS.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_TAGS.ID.notIn(keepIds)))
                .then();
    }

    private Mono<Void> deleteAllByAppAndClient(String appCode, String clientCode) {
        return Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_TAGS)
                        .where(ENTITY_PROCESSOR_TAGS.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_TAGS.CLIENT_CODE.eq(clientCode)))
                .then();
    }

    private static Tag toDto(EntityProcessorTagsRecord rec) {
        Tag tag = new Tag();
        tag.setId(rec.getId());
        tag.setAppCode(rec.getAppCode());
        tag.setClientCode(rec.getClientCode());
        tag.setName(rec.getName());
        tag.setActive(Boolean.TRUE.equals(rec.getIsActive()));
        tag.setColor(rec.getColor());
        tag.setIcon(rec.getIcon());
        tag.setCreatedBy(rec.getCreatedBy());
        tag.setUpdatedBy(rec.getUpdatedBy());
        return tag;
    }
}
