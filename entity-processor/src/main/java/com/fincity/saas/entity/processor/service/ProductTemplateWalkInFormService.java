package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
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

    private static final String PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE = "ProductTemplateWalkInForm";

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

    public Mono<ProductTemplateWalkInForm> create(ProductTemplateWalkInFormRequest req) {

        if (req.getProductTemplateId() == null || req.getStageId() == null || req.getStatusId() == null) {
            List<String> missingFields = new ArrayList<>();
            if (req.getProductTemplateId() == null)
                missingFields.add(ProcessorMessageResourceService.PRODUCT_TEMPLATE_ID_MISSING);
            if (req.getStageId() == null) missingFields.add(ProcessorMessageResourceService.STAGE_MISSING);
            if (req.getStatusId() == null) missingFields.add(ProcessorMessageResourceService.STATUS_MISSING);
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    String.join(",", missingFields),
                    this.getEntitySeries());
        }

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> Mono.zip(
                                productTemplateService.readIdentityWithAccess(access, req.getProductTemplateId()),
                                stageService.readIdentityWithAccess(access, req.getStageId()),
                                stageService.readIdentityWithAccess(access, req.getStatusId())),
                        (access, tuple3) -> {
                            ULong productTemplateId = tuple3.getT1().getId();
                            return this.dao
                                    .findByAppClientAndProductTemplate(access, productTemplateId)
                                    .flatMap(existing -> this.updateInternal(
                                            access,
                                            existing.setStageId(tuple3.getT2().getId())
                                                    .setStatusId(tuple3.getT3().getId())
                                                    .setAssignmentType(req.getAssignmentType())))
                                    .switchIfEmpty(this.createInternal(
                                            access,
                                            new ProductTemplateWalkInForm()
                                                    .setProductTemplateId(productTemplateId)
                                                    .setStageId(tuple3.getT2().getId())
                                                    .setStatusId(tuple3.getT3().getId())
                                                    .setAssignmentType(req.getAssignmentType())));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductTemplateWalkInFormService.create"));
    }

    @Override
    protected Mono<ProductTemplateWalkInForm> updatableEntity(ProductTemplateWalkInForm entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setAssignmentType(entity.getAssignmentType());
            existing.setStageId(entity.getStageId());
            existing.setStatusId(entity.getStatusId());
            return Mono.just(existing);
        });
    }
}
