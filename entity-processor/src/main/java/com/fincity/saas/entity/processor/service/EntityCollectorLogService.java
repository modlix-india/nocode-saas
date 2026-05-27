package com.fincity.saas.entity.processor.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.entity.processor.dao.EntityCollectorLogDAO;
import com.fincity.saas.entity.processor.dto.EntityCollectorLog;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorCollectorLogStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCollectorLogRecord;
import java.time.LocalDateTime;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EntityCollectorLogService
        extends AbstractJOOQUpdatableDataService<
                EntityProcessorCollectorLogRecord, ULong, EntityCollectorLog, EntityCollectorLogDAO> {

    @Autowired
    private EntityCollectorMessageResourceService entityCollectorMessageResponseService;

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

    public Mono<EntityCollectorLog> update(
            ULong logId,
            Map<String, Object> outgoingEntityData,
            EntityProcessorCollectorLogStatus status,
            String statusMessage) {
        EntityCollectorLog updatedLog = (EntityCollectorLog) new EntityCollectorLog()
                .setOutgoingEntityData(outgoingEntityData)
                .setStatus(status)
                .setStatusMessage(statusMessage)
                .setId(logId);

        return this.update(updatedLog);
    }

    public Mono<ULong> create(ULong entityIntegrationId, Map<String, Object> incomingEntityData, String ipAddress) {
        return entityCollectorMessageResponseService
                .getMessage(EntityCollectorMessageResourceService.INTEGRATION_FOUND_MESSAGE)
                .flatMap(message -> {
                    EntityCollectorLog log = new EntityCollectorLog()
                            .setEntityIntegrationId(entityIntegrationId)
                            .setIncomingEntityData(incomingEntityData)
                            .setIpAddress(ipAddress)
                            .setStatus(EntityProcessorCollectorLogStatus.IN_PROGRESS)
                            .setStatusMessage(message);

                    return super.create(log).map(EntityCollectorLog::getId);
                });
    }

    public Mono<EntityCollectorLog> updateOnError(ULong logId, String statusMessage) {

        EntityCollectorLog errorLog = (EntityCollectorLog) new EntityCollectorLog()
                .setStatus(EntityProcessorCollectorLogStatus.REJECTED)
                .setStatusMessage(statusMessage)
                .setId(logId);

        return this.update(errorLog);
    }
}
