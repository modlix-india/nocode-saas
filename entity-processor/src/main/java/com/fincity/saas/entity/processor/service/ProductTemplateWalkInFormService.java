package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateDAO;
import com.fincity.saas.entity.processor.dao.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormRecord;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
@RequiredArgsConstructor
public class ProductTemplateWalkInFormService extends BaseUpdatableService<
        EntityProcessorProductTemplatesWalkInFormRecord,
        ProductTemplateWalkInForm,
        ProductTemplateWalkInFormDAO> {

    private final ProductTemplateDAO productTemplateDAO;
    private final StageDAO stageDAO;
    private final ProductTemplateWalkInFormDAO productTemplateWalkInFormDAO;

    @Override
    protected Mono<ProductTemplateWalkInForm> updatableEntity(ProductTemplateWalkInForm entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setAssignmentType(entity.getAssignmentType());
            existing.setStageId(entity.getStageId());
            existing.setStatusId(entity.getStatusId());
            return Mono.just(existing);
        });
    }

    public Mono<ProductTemplateWalkInForm> create(ProductTemplateWalkInFormRequest productTemplateWalkInFormRequest) {
        if (productTemplateWalkInFormRequest.getProductTemplateId() == null
                || productTemplateWalkInFormRequest.getStageId() == null
                || productTemplateWalkInFormRequest.getStatusId() == null) {
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.INVALID_PARAMETERS);
        }

        ProductTemplateWalkInForm entity = ProductTemplateWalkInForm.of(productTemplateWalkInFormRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> Mono.zip(
                        productTemplateDAO.readInternal(access, entity.getProductTemplateId())
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        ProcessorMessageResourceService.PRODUCT_TEMPLATE_NOT_FOUND)),

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
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateWalkInFormService.create"));
    }

    @Override
    protected String getCacheName() {
        return "ProductTemplateWalkInFormCache";
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }
}
