package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.*;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormRecord;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.model.request.ProductWalkInFormRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import lombok.RequiredArgsConstructor;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
@RequiredArgsConstructor
public class ProductWalkInFormService extends BaseUpdatableService<EntityProcessorProductWalkInFormRecord, ProductWalkInForm, ProductWalkInFormDAO> {

    private final ProductDAO productDAO;
    private final StageDAO stageDAO;
    private final ProductWalkInFormDAO productWalkInFormDAO;

    private static final String PRODUCT_WALK_IN_FORM_CASH = "ProductWalkInForm";

    @Override
    protected String getCacheName() {
        return PRODUCT_WALK_IN_FORM_CASH;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
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

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_WALK_IN_FORM;
    }

    public Mono<ProductWalkInForm> create(ProductWalkInFormRequest productWalkInFormRequest) {

        if (productWalkInFormRequest.getProductId() == null
                || productWalkInFormRequest.getStageId() == null
                || productWalkInFormRequest.getStatusId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS);
        }

        ProductWalkInForm entity = ProductWalkInForm.of(productWalkInFormRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> Mono.zip(
                        productDAO.readInternal(access, entity.getProductId())
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_FOUND)),
                        stageDAO.readInternal(access, entity.getStageId())
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.STAGE_MISSING)),
                        stageDAO.readInternal(access, entity.getStatusId())
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.STATUS_MISSING))
                ).flatMap(tuple -> {
                    if (entity.getId() != null) {
                        return this.readById(access, entity.getId())
                                .flatMap(existing ->
                                        updatableEntity(entity)
                                                .flatMap(updated -> {
                                                    updated.setId(existing.getId());
                                                    return this.updateInternal(access, updated);
                                                })
                                )
                                .switchIfEmpty(this.createInternal(access, entity)); // If not found, create
                    } else {
                        return this.createInternal(access, entity);
                    }
                })
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductWalkInFormService.create"));
    }


}
