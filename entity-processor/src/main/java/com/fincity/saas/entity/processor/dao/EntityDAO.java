package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorEntities.ENTITY_PROCESSOR_ENTITIES;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;

@Component
public class EntityDAO extends BaseProcessorDAO<EntityProcessorEntitiesRecord, Entity> {

    protected EntityDAO() {
        super(Entity.class, ENTITY_PROCESSOR_ENTITIES, ENTITY_PROCESSOR_ENTITIES.ID);
    }
}
