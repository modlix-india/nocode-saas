package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Entity.Fields;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EntityService extends BaseProcessorService<EntityProcessorEntitiesRecord, Entity, EntityDAO> {

    private static final String ENTITY_CACHE = "entity";

    @Override
    protected String getCacheName() {
        return ENTITY_CACHE;
    }

    @Override
    protected Mono<Entity> checkEntity(Entity entity) {
        return null;
    }

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
