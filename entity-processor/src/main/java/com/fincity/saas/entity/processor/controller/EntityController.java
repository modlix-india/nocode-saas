package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.service.EntityService;

public class EntityController
        extends BaseProcessorController<EntityProcessorEntitiesRecord, Entity, EntityDAO, EntityService> {}
