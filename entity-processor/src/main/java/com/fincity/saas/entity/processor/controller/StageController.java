package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseValueController;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.StageService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/stages")
public class StageController extends BaseValueController<EntityProcessorStagesRecord, Stage, StageDAO, StageService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<BaseValueResponse<Stage>>> createFromRequest(@RequestBody StageRequest stageRequest) {
        return this.service.create(stageRequest).map(ResponseEntity::ok);
    }

    @GetMapping(PATH_VALUES)
    public Mono<ResponseEntity<List<BaseValueResponse<Stage>>>> getAllValues(
            @RequestParam(required = false, defaultValue = "PRE_QUALIFICATION") Platform platform,
            @RequestParam(required = false) StageType stageType,
            @RequestParam(required = false) ULong productTemplateId,
            @RequestParam(required = false) ULong parentId) {
        return this.service
                .getAllValues(platform, stageType, productTemplateId, parentId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_VALUES_ORDERED)
    public Mono<ResponseEntity<List<BaseValueResponse<Stage>>>> getValuesInOrder(
            @RequestParam(required = false, defaultValue = "PRE_QUALIFICATION") Platform platform,
            @RequestParam(required = false) StageType stageType,
            @RequestParam(required = false) ULong productTemplateId,
            @RequestParam(required = false) ULong parentId) {
        return this.service
                .getAllValuesInOrder(platform, stageType, productTemplateId, parentId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}
