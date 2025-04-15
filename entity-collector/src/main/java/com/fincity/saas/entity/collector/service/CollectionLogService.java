package com.fincity.saas.entity.collector.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.entity.collector.dao.CollectionLogDAO;
import com.fincity.saas.entity.collector.dto.CollectionLog;
import com.fincity.saas.entity.collector.jooq.tables.records.CollectionLogsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class CollectionLogService extends AbstractJOOQUpdatableDataService<CollectionLogsRecord, ULong, CollectionLog, CollectionLogDAO> {



    @Override
    protected Mono<CollectionLog> updatableEntity(CollectionLog entity) {
        return Mono.just(entity);
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong  key, Map<String, Object> fields) {
        return null;
    }
}
