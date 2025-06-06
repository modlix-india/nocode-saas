package com.fincity.saas.entity.collector.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.entity.collector.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import com.fincity.saas.entity.collector.service.EntityIntegrationService;
import lombok.RequiredArgsConstructor;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/entity/collector/integration")
@RequiredArgsConstructor
public class EntityIntegrationController extends AbstractJOOQUpdatableDataController<
        EntityIntegrationsRecord, ULong, EntityIntegration, EntityIntegrationDAO, EntityIntegrationService> {

}
