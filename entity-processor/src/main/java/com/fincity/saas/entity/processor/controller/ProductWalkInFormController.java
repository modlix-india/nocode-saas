package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ProductWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.request.ProductWalkInFormRequest;
import com.fincity.saas.entity.processor.service.ProductWalkInFormService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/forms")
public class ProductWalkInFormController
        extends BaseUpdatableController<
                EntityProcessorProductWalkInFormsRecord,
                ProductWalkInForm,
                ProductWalkInFormDAO,
                ProductWalkInFormService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductWalkInForm>> createFromRequest(
            @RequestBody ProductWalkInFormRequest productWalkInFormRequest) {
        return this.service.create(productWalkInFormRequest).map(ResponseEntity::ok);
    }
}
