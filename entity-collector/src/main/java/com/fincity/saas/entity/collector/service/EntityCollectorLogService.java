package com.fincity.saas.entity.collector.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.entity.collector.dao.EntityCollectorLogDAO;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class EntityCollectorLogService extends AbstractJOOQUpdatableDataService<
        EntityCollectorLogRecord, ULong, EntityCollectorLog, EntityCollectorLogDAO> {

    private final EntityCollectorMessageResourceService entityCollectorMessageResponseService;

    @Override
    protected Mono<EntityCollectorLog> updatableEntity(EntityCollectorLog entityCollectorLog) {
        return this.read(entityCollectorLog.getId()).map(existing -> {
            existing.setOutgoingEntityData(entityCollectorLog.getOutgoingEntityData());
            existing.setStatus(entityCollectorLog.getStatus());
            existing.setStatusMessage(entityCollectorLog.getStatusMessage());
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });
    }


    public Mono<EntityCollectorLog> update(ULong logId, Map<String, Object> outgoingEntityData, EntityCollectorLogStatus status, String statusMessage) {
        EntityCollectorLog updatedLog = (EntityCollectorLog) new EntityCollectorLog()
                .setOutgoingEntityData(outgoingEntityData)
                .setStatus(status)
                .setStatusMessage(statusMessage)
                .setId(logId);

        System.out.println("updatedLog"+ updatedLog);


        return super.update(updatedLog);
    }


    public Mono<ULong> create(ULong entityIntegrationId, Map<String, Object> incomingEntityData, String ipAddress) {
        return entityCollectorMessageResponseService.getMessage(EntityCollectorMessageResourceService.INTEGRATION_FOUND_MESSAGE)
                .flatMap(message -> {
                    EntityCollectorLog log = new EntityCollectorLog()
                            .setEntityIntegrationId(entityIntegrationId)
                            .setIncomingEntityData(incomingEntityData)
                            .setIpAddress(ipAddress)
                            .setStatus(EntityCollectorLogStatus.WITH_ERRORS)
                            .setStatusMessage(message);

                    return super.create(log).map(EntityCollectorLog::getId);
                });
    }

    public Mono<EntityCollectorLog> updateOnError(ULong logId, String statusMessage) {
        EntityCollectorLog errorLog = (EntityCollectorLog) new EntityCollectorLog()
                .setStatus(EntityCollectorLogStatus.REJECTED)
                .setStatusMessage(statusMessage)
                .setId(logId);
        return super.update(errorLog);
    }

}
