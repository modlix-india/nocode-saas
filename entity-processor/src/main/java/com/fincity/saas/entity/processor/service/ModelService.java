package com.fincity.saas.entity.processor.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.ModelDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;

import reactor.core.publisher.Mono;

@Service
public class ModelService extends BaseProcessorService<EntityProcessorModelsRecord, Model, ModelDAO> {

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
