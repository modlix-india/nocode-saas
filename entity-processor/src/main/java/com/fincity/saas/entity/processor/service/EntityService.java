package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.model.request.EntityRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class EntityService extends BaseProcessorService<EntityProcessorEntitiesRecord, Entity, EntityDAO> {

    private static final String ENTITY_CACHE = "entity";

    private final ProductService productService;
    private final ModelService modelService;
    private final ProductRuleService productRuleService;

    public EntityService(
            ProductService productService, ModelService modelService, ProductRuleService productRuleService) {
        this.productService = productService;
        this.modelService = modelService;
        this.productRuleService = productRuleService;
    }

    @Override
    protected String getCacheName() {
        return ENTITY_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY;
    }

    @Override
    protected Mono<Entity> checkEntity(Entity entity, Tuple3<String, String, ULong> accessInfo) {
        return this.checkEntity(entity).flatMap(uEntity -> this.setModel(accessInfo, uEntity));
    }

    @Override
    protected Mono<Entity> updatableEntity(Entity entity) {

        return FlatMapUtil.flatMapMono(() -> this.updateModel(entity), super::updatableEntity, (uEntity, existing) -> {
            existing.setDialCode(entity.getDialCode());
            existing.setPhoneNumber(entity.getPhoneNumber());
            existing.setEmail(entity.getEmail());
            existing.setSource(entity.getSource());
            existing.setSubSource(entity.getSubSource());
            existing.setStage(entity.getStage());
            existing.setStatus(entity.getStatus());

            return Mono.just(existing);
        });
    }

    public Mono<Entity> create(EntityRequest entityRequest) {

        Entity entity = Entity.of(entityRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.productService.checkAndUpdateIdentity(entityRequest.getProductId()),
                (hasAccess, productIdentity) -> Mono.just(entityRequest.setProductId(productIdentity)),
                (hasAccess, productIdentity, req) -> this.productRuleService.getUserAssignment(
                        hasAccess.getT1().getT1(),
                        hasAccess.getT1().getT2(),
                        productIdentity.getULongId(),
                        Platform.PRE_QUALIFICATION,
                        entity.toJson()),
                (hasAccess, productIdentity, req, userId) -> this.setEntityAssignment(entity, userId),
                (hasAccess, productIdentity, req, userId, aEnttiy) -> super.createInternal(aEnttiy, hasAccess));
    }

    public Mono<ProcessorResponse> createResponse(EntityRequest entityRequest) {
        return FlatMapUtil.flatMapMono(
                () -> this.create(entityRequest),
                cEntity -> Mono.just(ProcessorResponse.ofCreated(cEntity.getCode(), this.getEntitySeries())));
    }

    private Mono<Entity> checkEntity(Entity entity) {

        if (entity.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.productService.readById(entity.getProductId()),
                (ca, product) -> Mono.just(entity.setProductId(product.getId())),
                (ca, product, pEntity) ->
                        this.setDefaultStage(pEntity, product.getDefaultStageId(), product.getDefaultStatusId()));
    }

    private Mono<Entity> setDefaultStage(Entity entity, ULong defaultStageId, ULong defaultStatusId) {

        entity.setStage(defaultStageId);
        entity.setStatus(defaultStatusId);

        return Mono.just(entity);
    }

    private Mono<Entity> setModel(Tuple3<String, String, ULong> accessInfo, Entity entity) {
        return this.modelService.getOrCreateEntityModel(accessInfo, entity);
    }

    private Mono<Entity> updateModel(Entity entity) {
        return this.modelService.getOrCreateEntityPhoneModel(entity.getAppCode(), entity.getClientCode(), entity);
    }

    private Mono<Entity> setEntityAssignment(Entity entity, ULong userId) {
        entity.setAddedByUserId(userId);
        entity.setCurrentUserId(userId);
        return Mono.just(entity);
    }
}
