package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.OwnerDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorOwnersRecord;
import com.fincity.saas.entity.processor.model.request.OwnerRequest;
import com.fincity.saas.entity.processor.service.OwnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/owners")
public class OwnerController
        extends BaseProcessorController<EntityProcessorOwnersRecord, Owner, OwnerDAO, OwnerService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Owner>> createFromRequest(@RequestBody OwnerRequest ownerRequest) {
        return this.service.create(ownerRequest).map(ResponseEntity::ok);
    }
}
