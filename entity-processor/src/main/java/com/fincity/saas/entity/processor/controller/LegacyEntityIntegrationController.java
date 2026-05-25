package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.entity.processor.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorIntegrationsRecord;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy backward-compatible controller for entity integrations.
 * Mirrors {@link EntityIntegrationController} exactly but keeps the
 * original {@code /api/entity/collector/integration} path so existing
 * UI clients (marketingai.leadFormTable, leadzump page-event functions)
 * keep working until they're migrated to the new processor path.
 */
@RestController
@RequestMapping("/api/entity/collector/integration")
public class LegacyEntityIntegrationController
        extends AbstractJOOQUpdatableDataController<
                EntityProcessorIntegrationsRecord, ULong, EntityIntegration,
                EntityIntegrationDAO, EntityIntegrationService> {
}
