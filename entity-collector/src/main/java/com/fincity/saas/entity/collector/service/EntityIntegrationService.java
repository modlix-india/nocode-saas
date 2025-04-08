package com.fincity.saas.entity.collector.service;

import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.tables.records.NodeIntegrationsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import static com.fincity.saas.entity.collector.jooq.tables.NodeIntegrations.NODE_INTEGRATIONS;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntityIntegrationService {

    private final DSLContext dsl;

    public void saveIntegration(EntityIntegration dto) {
        NodeIntegrationsRecord record = dsl.newRecord(NODE_INTEGRATIONS);

        record.setAppCode(dto.getAppCode());
        record.setClientCode(dto.getClientCode());
        record.setTarget(dto.getTarget());
        record.setInSource(dto.getInSource());

        record.insert();
    }
}
