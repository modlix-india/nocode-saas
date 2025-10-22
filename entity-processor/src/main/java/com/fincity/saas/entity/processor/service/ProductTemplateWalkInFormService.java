package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProductTemplateWalkInFormService
        extends BaseUpdatableService<
                EntityProcessorProductTemplatesWalkInFormsRecord,
                ProductTemplateWalkInForm,
                ProductTemplateWalkInFormDAO> {

    private static final String PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE = "productTemplateWalkInForm";

    private final ProductTemplateService productTemplateService;
    private final StageService stageService;

    public ProductTemplateWalkInFormService(ProductTemplateService productTemplateService, StageService stageService) {
        this.productTemplateService = productTemplateService;
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    public Mono<ProductTemplateWalkInForm> create(ProductTemplateWalkInFormRequest formRequest) {

        if (formRequest.getProductTemplateId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    productTemplateService.getEntityName());
        }

        if (formRequest.getStageId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    stageService.getEntityName());
        }

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> productTemplateService.readIdentityWithAccess(
                                access, formRequest.getProductTemplateId()),
                        (access, productTemplate) -> stageService
                                .getParentChild(
                                        access,
                                        productTemplate.getId(),
                                        formRequest.getStageId(),
                                        formRequest.getStatusId())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.STAGE_MISSING)),
                        (access, productTemplate, stageStatusEntity) -> dao.findByAppClientAndProductTemplate(
                                        access, productTemplate.getId())
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
                                        new ProductTemplateWalkInForm()
                                                .setProductTemplateId(productTemplate.getId())
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
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateWalkInFormService.create"));
    }

    @Override
    protected Mono<ProductTemplateWalkInForm> updatableEntity(ProductTemplateWalkInForm entity) {
        return super.updatableEntity(entity).map(existing -> {
            existing.setAssignmentType(entity.getAssignmentType());
            existing.setStageId(entity.getStageId());
            existing.setStatusId(entity.getStatusId());
            return existing;
        });
    }
}
