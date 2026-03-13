package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.entity.processor.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorIntegrationsRecord;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entity/processor/collector/integration")
public class EntityIntegrationController
        extends AbstractJOOQUpdatableDataController<
                EntityProcessorIntegrationsRecord, ULong, EntityIntegration,
                EntityIntegrationDAO, EntityIntegrationService> {
}
