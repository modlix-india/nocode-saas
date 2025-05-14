package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dao.ModelDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import com.fincity.saas.entity.processor.model.request.ModelRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ModelService extends BaseProcessorService<EntityProcessorModelsRecord, Model, ModelDAO> {

    private static final String MODEL_CACHE = "model";

    @Override
    protected String getCacheName() {
        return MODEL_CACHE;
    }

    @Override
    protected Mono<Model> checkEntity(Model entity, Tuple3<String, String, ULong> accessInfo) {
        return Mono.just(entity);
    }

    @Override
    protected Mono<Model> updatableEntity(Model entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDialCode(entity.getDialCode());
            existing.setPhoneNumber(entity.getPhoneNumber());
            existing.setEmail(entity.getEmail());

            return Mono.just(existing);
        });
    }

    public Mono<Model> create(ModelRequest modelRequest) {
        return this.create(Model.of(modelRequest));
    }

    public Mono<Entity> getOrCreateEntityModel(Tuple3<String, String, ULong> accessInfo, Entity entity) {

        if (entity.getModelId() != null)
            return FlatMapUtil.flatMapMono(
                    () -> this.readIdentityInternal(ULongUtil.valueOf(entity.getModelId())), model -> {
                        entity.setModelId(model.getId());

                        if (model.getDialCode() != null && model.getPhoneNumber() != null) {
                            entity.setDialCode(model.getDialCode());
                            entity.setPhoneNumber(model.getPhoneNumber());
                        }

                        if (model.getEmail() != null) entity.setEmail(model.getEmail());

                        return Mono.just(entity);
                    });

        return getOrCreateEntityPhoneModel(accessInfo.getT1(), accessInfo.getT2(), entity);
    }

    public Mono<Entity> getOrCreateEntityPhoneModel(String appCode, String clientCode, Entity entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao
                        .readByNumberAndEmail(
                                appCode, clientCode, entity.getDialCode(), entity.getPhoneNumber(), entity.getEmail())
                        .switchIfEmpty(this.create(Model.of(entity))),
                model -> {
                    if (model.getId() == null)
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.MODEL_NOT_CREATED);

                    return Mono.just(entity.setModelId(model.getId()));
                });
    }
}
