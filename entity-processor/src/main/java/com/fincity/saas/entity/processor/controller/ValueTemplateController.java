package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.ValueTemplateDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRequest;
import com.fincity.saas.entity.processor.service.ValueTemplateService;
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
public class ValueTemplateController
        extends BaseController<
                EntityProcessorValueTemplatesRecord, ValueTemplate, ValueTemplateDAO, ValueTemplateService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ValueTemplate>> createFromRequest(
            @RequestBody ValueTemplateRequest valueTemplateRequest) {
        return this.service.create(valueTemplateRequest).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.deleteIdentity(identity);
    }
}
