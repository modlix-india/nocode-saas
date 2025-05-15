package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.EntityDAO;
import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorEntitiesRecord;
import com.fincity.saas.entity.processor.model.request.EntityRequest;
import com.fincity.saas.entity.processor.service.EntityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/entities")
public class EntityController
        extends BaseProcessorController<EntityProcessorEntitiesRecord, Entity, EntityDAO, EntityService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Entity>> createFromRequest(@RequestBody EntityRequest entityRequest) {
        return this.service.create(entityRequest).map(ResponseEntity::ok);
    }
}
