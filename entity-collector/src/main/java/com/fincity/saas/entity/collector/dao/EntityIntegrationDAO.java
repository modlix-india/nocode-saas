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

}