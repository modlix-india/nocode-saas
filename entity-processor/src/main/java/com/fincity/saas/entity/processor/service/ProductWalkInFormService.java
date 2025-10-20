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
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
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

    public Mono<ProductWalkInForm> create(ProductWalkInFormRequest req) {
        if (req.getProductId() == null || req.getStageId() == null || req.getStatusId() == null) {
            List<String> missingFields = new ArrayList<>();
            if (req.getProductId() == null) missingFields.add(ProcessorMessageResourceService.PRODUCT_ID_MISSING);
            if (req.getStageId() == null) missingFields.add(ProcessorMessageResourceService.STAGE_MISSING);
            if (req.getStatusId() == null) missingFields.add(ProcessorMessageResourceService.STATUS_MISSING);
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg), String.join(",", missingFields));
        }

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> Mono.zip(
                                productService.readIdentityWithAccess(access, req.getProductId()),
                                stageService.readIdentityWithAccess(access, req.getStageId()),
                                stageService.readIdentityWithAccess(access, req.getStatusId())),
                        (access, tuple3) -> {
                            ULong productId = tuple3.getT1().getId();
                            return this.dao
                                    .findByAppClientAndProductId(access, productId)
                                    .flatMap(existing -> this.updateInternal(
                                            access,
                                            existing.setStageId(tuple3.getT2().getId())
                                                    .setStatusId(tuple3.getT3().getId())
                                                    .setAssignmentType(req.getAssignmentType())))
                                    .switchIfEmpty(this.createInternal(
                                            access,
                                            new ProductWalkInForm()
                                                    .setProductId(productId)
                                                    .setStageId(tuple3.getT2().getId())
                                                    .setStatusId(tuple3.getT3().getId())
                                                    .setAssignmentType(req.getAssignmentType())));
                        })
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
