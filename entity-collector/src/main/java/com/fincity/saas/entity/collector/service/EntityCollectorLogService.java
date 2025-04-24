package com.fincity.saas.entity.collector.service;

import com.fincity.saas.entity.collector.dao.EntityCollectorLogDAO;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class EntityCollectorLogService {

    private final EntityCollectorLogDAO dao;

    public EntityCollectorLogService(EntityCollectorLogDAO dao) {
        this.dao = dao;
    }

    public Mono<ULong> createInitialLog(ULong entityIntegrationId, Map<String, Object> incomingEntityData, String ipAddress) {
        EntityCollectorLog log = new EntityCollectorLog()
                .setEntityIntegrationId(entityIntegrationId)
                .setIncomingEntityData(incomingEntityData)
                .setIpAddress(ipAddress)
                .setStatus(EntityCollectorLogStatus.WITH_ERRORS)
                .setStatusMessage("Integration found. Processing started.");

        return dao.create(log).map(EntityCollectorLog::getId);
    }

    public Mono<EntityCollectorLog> updateLogWithOutgoingEntity(ULong logId, Map<String, Object> outgoingEntityData, EntityCollectorLogStatus status, String statusMessage) {
        return dao.getById(logId)
                .flatMap(existingLog -> {
                    existingLog
                            .setOutgoingEntityData(outgoingEntityData)
                            .setStatus(status)
                            .setStatusMessage(statusMessage);
                    return dao.update(existingLog);
                });
    }

    public Mono<EntityCollectorLog> updateLogStatus(ULong logId, EntityCollectorLogStatus status, String statusMessage) {
        return dao.getById(logId)
                .flatMap(existingLog -> {
                    existingLog
                            .setStatus(status)
                            .setStatusMessage(statusMessage);
                    return dao.update(existingLog);
                });
    }

    public Mono<EntityCollectorLog> getLogById(ULong logId) {
        return dao.getById(logId);
    }
}
