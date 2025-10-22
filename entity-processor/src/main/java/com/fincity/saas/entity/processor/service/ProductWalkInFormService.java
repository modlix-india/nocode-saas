package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.request.ProductWalkInFormRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductWalkInFormService
        extends BaseUpdatableService<EntityProcessorProductWalkInFormsRecord, ProductWalkInForm, ProductWalkInFormDAO> {

    private static final String PRODUCT_WALK_IN_FORM_CACHE = "ProductWalkInForm";

    private final ProductService productService;
    private final StageService stageService;

    public ProductWalkInFormService(ProductService productService, StageService stageService) {
        this.productService = productService;
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_WALK_IN_FORM_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_WALK_IN_FORMS;
    }

    public Mono<ProductWalkInForm> create(ProductWalkInFormRequest formRequest) {

        if (formRequest.getProductId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    productService.getEntityName());
        }

        if (formRequest.getStageId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    stageService.getEntityName());
        }

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> productService.readIdentityWithAccess(access, formRequest.getProductId()),
                        (access, product) -> stageService
                                .getParentChild(
                                        access,
                                        product.getProductTemplateId(),
                                        formRequest.getStageId(),
                                        formRequest.getStatusId())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.STAGE_MISSING)),
                        (access, product, stageStatusEntity) -> dao.findByAppClientAndProductId(access, product.getId())
                                .flatMap(existing -> super.updateInternal(
                                        access,
                                        existing.setStageId(stageStatusEntity
                                                        .getKey()
                                                        .getId())
                                                .setStatusId(
                                                        stageStatusEntity
                                                                        .getValue()
                                                                        .isEmpty()
                                                                ? null
                                                                : stageStatusEntity
                                                                        .getValue()
                                                                        .getFirst()
                                                                        .getId())
                                                .setAssignmentType(formRequest.getAssignmentType())))
                                .switchIfEmpty(super.createInternal(
                                        access,
                                        new ProductWalkInForm()
                                                .setProductId(product.getId())
                                                .setStageId(stageStatusEntity
                                                        .getKey()
                                                        .getId())
                                                .setStatusId(
                                                        stageStatusEntity
                                                                        .getValue()
                                                                        .isEmpty()
                                                                ? null
                                                                : stageStatusEntity
                                                                        .getValue()
                                                                        .getFirst()
                                                                        .getId())
                                                .setAssignmentType(formRequest.getAssignmentType()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.create"));
    }

    @Override
    protected Mono<ProductWalkInForm> updatableEntity(ProductWalkInForm entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setAssignmentType(entity.getAssignmentType());
            existing.setStageId(entity.getStageId());
            existing.setStatusId(entity.getStatusId());
            return Mono.just(existing);
        });
    }
}
