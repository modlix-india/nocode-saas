package com.fincity.saas.entity.collector.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;

import static com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations.ENTITY_INTEGRATIONS;

@Repository
public class EntityIntegrationDAO extends AbstractUpdatableDAO<EntityIntegrationsRecord, ULong, EntityIntegration> {


    protected EntityIntegrationDAO() {
        super(EntityIntegration.class, ENTITY_INTEGRATIONS, ENTITY_INTEGRATIONS.ID);
    }

//    public List<EntityIntegration> findAll() {
//        return dsl.selectFrom(ENTITY_INTEGRATIONS)
//                .fetchInto(EntityIntegration.class);
//    }
//
//    public Optional<EntityIntegration> findById(Long id) {
//        return dsl.selectFrom(ENTITY_INTEGRATIONS)
//                .where(ENTITY_INTEGRATIONS.ID.eq(ULong.valueOf(id)))
//                .fetchOptionalInto(EntityIntegration.class);
//    }
//
//    public List<EntityIntegration> findByClientCodeAndAppCode(String clientCode, String appCode) {
//        return dsl.selectFrom(ENTITY_INTEGRATIONS)
//                .where(ENTITY_INTEGRATIONS.CLIENT_CODE.eq(clientCode)
//                        .and(ENTITY_INTEGRATIONS.APP_CODE.eq(appCode)))
//                .fetchInto(EntityIntegration.class);
//    }
//
//    public List<EntityIntegration> findByClientCode(String clientCode) {
//        return dsl.selectFrom(ENTITY_INTEGRATIONS)
//                .where(ENTITY_INTEGRATIONS.CLIENT_CODE.eq(clientCode))
//                .fetchInto(EntityIntegration.class);
//    }
//
//    public void deleteById(Long id) {
//        dsl.deleteFrom(ENTITY_INTEGRATIONS)
//                .where(ENTITY_INTEGRATIONS.ID.eq(ULong.valueOf(id)))
//                .execute();
//    }
}
