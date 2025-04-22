package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.ModelDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ModelService extends BaseProcessorService<EntityProcessorModelsRecord, Model, ModelDAO> {

    private static final String MODEL_CACHE = "model";

    @Override
    protected String getCacheName() {
        return MODEL_CACHE;
    }

    @Override
    protected Mono<Model> updatableEntity(Model entity) {
        return super.updatableEntity(entity).flatMap(e -> {
            e.setDialCode(entity.getDialCode());
            e.setPhoneNumber(entity.getPhoneNumber());
            e.setEmail(entity.getEmail());
            e.setSource(entity.getSource());
            e.setSubSource(entity.getSubSource());

            return Mono.just(e);
        });
    }
}
