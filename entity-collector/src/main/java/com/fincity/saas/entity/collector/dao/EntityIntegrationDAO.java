package com.fincity.saas.entity.collector.dao;

import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations.ENTITY_INTEGRATIONS;

@Repository
@RequiredArgsConstructor
public class EntityIntegrationDAO {

    private final DSLContext dsl;

    public List<EntityIntegration> findAll() {
        return dsl.selectFrom(ENTITY_INTEGRATIONS)
                .fetchInto(EntityIntegration.class);
    }

    public Optional<EntityIntegration> findById(Long id) {
        return dsl.selectFrom(ENTITY_INTEGRATIONS)
                .where(ENTITY_INTEGRATIONS.ID.eq(ULong.valueOf(id)))
                .fetchOptionalInto(EntityIntegration.class);
    }

    public List<EntityIntegration> findByClientCodeAndAppCode(String clientCode, String appCode) {
        return dsl.selectFrom(ENTITY_INTEGRATIONS)
                .where(ENTITY_INTEGRATIONS.CLIENT_CODE.eq(clientCode)
                        .and(ENTITY_INTEGRATIONS.APP_CODE.eq(appCode)))
                .fetchInto(EntityIntegration.class);
    }

    public EntityIntegration insert(EntityIntegration entity) {
        return dsl.insertInto(ENTITY_INTEGRATIONS)
                .set(ENTITY_INTEGRATIONS.CLIENT_CODE, entity.getClientCode())
                .set(ENTITY_INTEGRATIONS.APP_CODE, entity.getAppCode())
                .set(ENTITY_INTEGRATIONS.TARGET, entity.getTarget())
                .set(ENTITY_INTEGRATIONS.SECONDARY_TARGET, entity.getSecondaryTarget())
                .set(ENTITY_INTEGRATIONS.IN_SOURCE, entity.getInSource())
                .set(ENTITY_INTEGRATIONS.IN_SOURCE_TYPE, entity.getInSourceType() != null
                        ? EntityIntegrationsInSourceType.valueOf(entity.getInSourceType().name())
                        : null)
                .returning()
                .fetchOne()
                .into(EntityIntegration.class);
    }

    public EntityIntegration update(EntityIntegration entity) {
        return dsl.update(ENTITY_INTEGRATIONS)
                .set(ENTITY_INTEGRATIONS.CLIENT_CODE, entity.getClientCode())
                .set(ENTITY_INTEGRATIONS.APP_CODE, entity.getAppCode())
                .set(ENTITY_INTEGRATIONS.TARGET, entity.getTarget())
                .set(ENTITY_INTEGRATIONS.SECONDARY_TARGET, entity.getSecondaryTarget())
                .set(ENTITY_INTEGRATIONS.IN_SOURCE, entity.getInSource())
                .set(ENTITY_INTEGRATIONS.IN_SOURCE_TYPE, entity.getInSourceType() != null
                        ? EntityIntegrationsInSourceType.valueOf(entity.getInSourceType().name())
                        : null)
                .where(ENTITY_INTEGRATIONS.ID.eq(ULong.valueOf(entity.getId())))
                .returning()
                .fetchOne()
                .into(EntityIntegration.class);
    }

    public List<EntityIntegration> findByClientCode(String clientCode) {
        return dsl.selectFrom(ENTITY_INTEGRATIONS)
                .where(ENTITY_INTEGRATIONS.CLIENT_CODE.eq(clientCode))
                .fetchInto(EntityIntegration.class);
    }

    public void deleteById(Long id) {
        dsl.deleteFrom(ENTITY_INTEGRATIONS)
                .where(ENTITY_INTEGRATIONS.ID.eq(ULong.valueOf(id)))
                .execute();
    }
}
