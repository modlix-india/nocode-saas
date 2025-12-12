package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.product.template.ProductTemplateDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.product.teamplate.ProductTemplateRequest;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/templates")
public class ProductTemplateController
        extends BaseUpdatableController<
                EntityProcessorProductTemplatesRecord, ProductTemplate, ProductTemplateDAO, ProductTemplateService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductTemplate>> createRequest(
            @RequestBody ProductTemplateRequest productTemplateRequest) {
        return this.service.createRequest(productTemplateRequest).map(ResponseEntity::ok);
    }

    @PostMapping(REQ_PATH_ID + "/attach")
    public Mono<ResponseEntity<ProductTemplate>> attachEntity(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity,
            @RequestBody ProductTemplateRequest productTemplateRequest) {
        return this.service.attachEntity(identity, productTemplateRequest).map(ResponseEntity::ok);
    }
}
