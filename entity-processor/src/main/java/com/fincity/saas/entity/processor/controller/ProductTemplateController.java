package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.ProductTemplateDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductTemplateRequest;
import com.fincity.saas.entity.processor.service.ProductTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/values/templates")
public class ProductTemplateController
        extends BaseController<
                EntityProcessorValueTemplatesRecord, ProductTemplate, ProductTemplateDAO, ProductTemplateService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductTemplate>> createFromRequest(
            @RequestBody ProductTemplateRequest productTemplateRequest) {
        return this.service.create(productTemplateRequest).map(ResponseEntity::ok);
    }

    @PostMapping(REQ_PATH_ID + "/attach")
    public Mono<ResponseEntity<ProductTemplate>> createFromRequest(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity,
            @RequestBody ProductTemplateRequest productTemplateRequest) {
        return this.service.attachEntity(identity, productTemplateRequest).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.deleteIdentity(identity);
    }
}
