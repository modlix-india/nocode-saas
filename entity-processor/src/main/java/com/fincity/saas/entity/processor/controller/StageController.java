package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseValueController;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.service.StageService;
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
@RequestMapping("api/entity/processor/stages")
public class StageController extends BaseValueController<EntityProcessorStagesRecord, Stage, StageDAO, StageService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Stage>> createFromRequest(@RequestBody StageRequest stageRequest) {
        return this.service.create(stageRequest).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.deleteIdentity(identity);
    }
}
