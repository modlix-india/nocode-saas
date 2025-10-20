package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.request.ProductTemplateWalkInFormRequest;
import com.fincity.saas.entity.processor.service.ProductTemplateWalkInFormService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/templates/forms")
public class ProductTemplateWalkInFormController
        extends BaseUpdatableController<
                EntityProcessorProductTemplatesWalkInFormsRecord,
                ProductTemplateWalkInForm,
                ProductTemplateWalkInFormDAO,
                ProductTemplateWalkInFormService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductTemplateWalkInForm>> createFromRequest(
            @RequestBody ProductTemplateWalkInFormRequest productTemplateWalkInFormRequest) {
        return this.service.create(productTemplateWalkInFormRequest).map(ResponseEntity::ok);
    }
}
