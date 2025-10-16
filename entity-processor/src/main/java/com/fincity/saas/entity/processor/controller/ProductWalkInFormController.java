package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ProductWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormRecord;
import com.fincity.saas.entity.processor.model.request.ProductWalkInFormRequest;
import com.fincity.saas.entity.processor.service.ProductWalkInFormService;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/walk-in-form")
public class ProductWalkInFormController extends BaseUpdatableController<EntityProcessorProductWalkInFormRecord, ProductWalkInForm, ProductWalkInFormDAO, ProductWalkInFormService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductWalkInForm>> createFromRequest(
            @RequestBody ProductWalkInFormRequest productWalkInFormRequest) {
        return this.service.create(productWalkInFormRequest).map(ResponseEntity::ok);
    }



}
