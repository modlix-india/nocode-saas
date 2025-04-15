package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.entity.collector.dao.EntityCollectorLogDAO;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class EntityCollectorLogService extends AbstractJOOQUpdatableDataService<
        EntityCollectorLogRecord, ULong, EntityCollectorLog, EntityCollectorLogDAO> {


    public void addEntityLog(ULong entityIntegrationId, JsonNode incomingLead, String ipAddress,
                             JsonNode outgoingLead, EntityCollectorLogStatus status, String statusMessage) {

        this.dao.create(new EntityCollectorLog()
                        .setEntityIntegrationId(entityIntegrationId)
                        .setIncomingLeadData(incomingLead)
                        .setIpAddress(ipAddress)
                        .setOutgoingLeadData(outgoingLead)
                        .setStatus(status)
                        .setStatusMessage(statusMessage))
                .subscribe();
    }

    @Override
    protected Mono<EntityCollectorLog> updatableEntity(EntityCollectorLog entity) {
        return Mono.just(entity);
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        return Mono.just(fields);
    }
}
