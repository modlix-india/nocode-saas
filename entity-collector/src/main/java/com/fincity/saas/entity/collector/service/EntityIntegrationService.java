package com.fincity.saas.entity.collector.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.collector.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class EntityIntegrationService
        extends AbstractJOOQUpdatableDataService<
        EntityIntegrationsRecord, ULong, EntityIntegration, EntityIntegrationDAO> {


    public Mono<EntityIntegration> findByInSourceAndType(String inSource, EntityIntegrationsInSourceType inSourceType) {
        return this.dao.findByInSourceAndInSourceType(inSource, inSourceType);
    }

    @Override
    public Mono<EntityIntegration> updatableEntity(EntityIntegration entity) {
        return this.read(entity.getId()).flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {
                    // TO:DO add fields
                    return existing;
                }));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        Map<String, Object> newFields = new HashMap<>();

        // TO:DO add updatable fields to map

        return Mono.just(newFields);
    }
}
