package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.model.EntityRequest;
import com.fincity.saas.entity.processor.model.base.Identity;
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
    private final StageService stageService;
    private final SourceService sourceService;
    private final ModelService modelService;

    public EntityService(
            ProductService productService,
            StageService stageService,
            SourceService sourceService,
            ModelService modelService) {
        this.productService = productService;
        this.stageService = stageService;
        this.sourceService = sourceService;
        this.modelService = modelService;
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

        return FlatMapUtil.flatMapMono(() -> this.updateModel(entity), super::updatableEntity, (uEntity, e) -> {
            e.setDialCode(entity.getDialCode());
            e.setPhoneNumber(entity.getPhoneNumber());
            e.setEmail(entity.getEmail());
            e.setSource(entity.getSource());
            e.setSubSource(entity.getSubSource());
            e.setStage(entity.getStage());
            e.setStatus(entity.getStatus());

            return Mono.just(e);
        });
    }

    public Mono<ProcessorResponse> create(EntityRequest entityRequest) {

        Entity entity = Entity.of(entityRequest);

        return FlatMapUtil.flatMapMono(
                () -> this.getEntityProduct(entityRequest.getProductId()),
                product -> super.create(entity.setProductId(product.getId())),
                (product, cEntity) ->
                        Mono.just(ProcessorResponse.ofCreated(cEntity.getCode(), this.getEntitySeries())));
    }

    private Mono<Product> getEntityProduct(Identity identity) {

        if (identity == null || identity.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.PRODUCT_IDENTITY_MISSING);

        return (identity.getId() == null
                        ? this.productService.readByCode(identity.getCode())
                        : this.productService.readInternal(ULongUtil.valueOf(identity.getId())))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.PRODUCT_IDENTITY_WRONG));
    }

    private Mono<Entity> checkEntity(Entity entity) {

        if (entity.getProductId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.PRODUCT_IDENTITY_MISSING);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.productService.readInternal(entity.getProductId()),
                (ca, product) -> Mono.just(entity.setProductId(product.getId())),
                (ca, product, pEntity) -> this.updateSources(ca, pEntity, product.getDefaultSource()),
                (ca, product, pEntity, sEntity) -> this.setDefaultStage(sEntity, product.getDefaultStage()));
    }

    private Mono<Entity> updateSources(ContextAuthentication ca, Entity entity, ULong defaultSourceId) {

        if (StringUtil.safeIsBlank(entity.getSource())) return this.setDefaultSource(entity, defaultSourceId);

        entity.setSource(StringUtil.toTitleCase(entity.getSource()));

        if (entity.getSubSource() != null) entity.setSubSource(StringUtil.toTitleCase(entity.getSubSource()));

        return sourceService
                .isValidParentChild(
                        ca.getUrlAppCode(),
                        ca.getUrlClientCodeOrElse(!ca.isAuthenticated()),
                        entity.getProductId(),
                        entity.getSource(),
                        entity.getSubSource())
                .flatMap(isValid -> Boolean.FALSE.equals(isValid)
                        ? this.setDefaultSource(entity, defaultSourceId)
                        : Mono.just(entity));
    }

    private Mono<Entity> setDefaultStage(Entity entity, ULong defaultStageId) {
        return this.stageService
                .readInternal(defaultStageId)
                .flatMap(defaultStage -> Mono.just(entity.setStage(defaultStage.getName())));
    }

    private Mono<Entity> setDefaultSource(Entity entity, ULong defaultSourceId) {
        return sourceService
                .readInternal(defaultSourceId)
                .flatMap(defaultSource -> Mono.just(entity.setSource(defaultSource.getName())));
    }

    private Mono<Entity> setModel(Tuple3<String, String, ULong> accessInfo, Entity entity) {
        return this.modelService.getOrCreateEntityModel(accessInfo, entity);
    }

    private Mono<Entity> updateModel(Entity entity) {
        return this.modelService.getOrCreateEntityPhoneModel(entity.getAppCode(), entity.getClientCode(), entity);
    }
}
