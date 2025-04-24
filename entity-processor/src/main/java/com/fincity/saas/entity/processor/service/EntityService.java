package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Entity.Fields;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.model.EntityRequest;
import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EntityService extends BaseProcessorService<EntityProcessorEntitiesRecord, Entity, EntityDAO> {

    private static final String ENTITY_CACHE = "entity";

    private final ProductService productService;
    private final StageService stageService;
    private final SourceService sourceService;

    public EntityService(ProductService productService, StageService stageService, SourceService sourceService) {
        this.productService = productService;
        this.stageService = stageService;
        this.sourceService = sourceService;
    }

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

    private Mono<EntityRequest> updateRequest(EntityRequest entityRequest) {
        Identity identity = entityRequest.getProductId();

        if (identity == null || identity.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.PRODUCT_IDENTITY_MISSING);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> identity.getId() == null
                        ? this.productService.readByCode(identity.getCode())
                        : this.productService
                                .readInternal(ULongUtil.valueOf(identity.getId()))
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_IDENTITY_WRONG)),
                (ca, product) -> Mono.just(
                        entityRequest.setProductId(Identity.of(product.getId().toBigInteger(), product.getCode()))),
                (ca, product, uProduct) -> this.checkSources(ca, entityRequest, product.getDefaultSource()));
    }

    private Mono<EntityRequest> checkSources(
            ContextAuthentication ca, EntityRequest entityRequest, ULong defaultSourceId) {

        if (StringUtil.safeIsBlank(entityRequest.getSource())) return setDefaultSource(entityRequest, defaultSourceId);

        entityRequest.setSource(StringUtil.toTitleCase(entityRequest.getSource()));
        entityRequest.setSubSource(StringUtil.toTitleCase(entityRequest.getSubSource()));

        return sourceService
                .isValidParentChild(
                        ca.getUrlAppCode(),
                        ca.getUrlClientCodeOrElse(!ca.isAuthenticated()),
                        ULongUtil.valueOf(entityRequest.getProductId().getId()),
                        entityRequest.getSource(),
                        entityRequest.getSubSource())
                .flatMap(isValid -> Boolean.FALSE.equals(isValid)
                        ? this.setDefaultSource(entityRequest, defaultSourceId)
                        : Mono.just(entityRequest));
    }

    private Mono<EntityRequest> setDefaultSource(EntityRequest entityRequest, ULong defaultSourceId) {
        return sourceService
                .readInternal(defaultSourceId)
                .flatMap(defaultSource -> Mono.just(entityRequest.setSource(defaultSource.getName())));
    }
}
