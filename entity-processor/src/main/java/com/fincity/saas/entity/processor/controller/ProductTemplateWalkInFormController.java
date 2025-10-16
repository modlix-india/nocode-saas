package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.service.ProductTemplateWalkInFormService;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/templates/walk-in-form")
public class ProductTemplateWalkInFormController extends BaseUpdatableController<EntityProcessorProductTemplatesWalkInFormRecord, ProductTemplateWalkInForm, ProductTemplateWalkInFormDAO, ProductTemplateWalkInFormService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductTemplateWalkInForm>> createFromRequest(
            @RequestBody ProductTemplateWalkInFormRequest productTemplateWalkInFormRequest) {
        return this.service.create(productTemplateWalkInFormRequest).map(ResponseEntity::ok);
    }


}
