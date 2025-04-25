package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.service.EntityService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity-processor/entities")
public class EntityController
        extends BaseProcessorController<EntityProcessorEntitiesRecord, Entity, EntityDAO, EntityService> {}
