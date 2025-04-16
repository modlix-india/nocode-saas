package com.fincity.saas.entity.processor.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Entity.Fields;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;

import reactor.core.publisher.Mono;

@Service
public class EntityService extends BaseProcessorService<EntityProcessorEntitiesRecord, Entity, EntityDAO> {

    @Override
    protected Mono<Entity> updatableEntity(Entity entity) {
        return super.updatableEntity(entity).flatMap(e -> {
            e.setDialCode(entity.getDialCode());
            e.setPhoneNumber(entity.getPhoneNumber());
            e.setEmail(entity.getEmail());
            e.setSource(entity.getSource());
            e.setSubSource(entity.getSubSource());

            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        return super.updatableFields(key, fields).flatMap(f -> {
            f.remove(Fields.modelId);
            f.remove(Fields.productId);

            return Mono.just(f);
        });
    }
}
