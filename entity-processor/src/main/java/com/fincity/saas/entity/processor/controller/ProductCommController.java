package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.service.ProductCommService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/productComms")
public class ProductCommController
        extends BaseUpdatableController<
                EntityProcessorProductCommsRecord, ProductComm, ProductCommDAO, ProductCommService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductComm>> createFromRequest(@RequestBody ProductCommRequest request) {
        return this.service.create(request).map(ResponseEntity::ok);
    }

    @PostMapping("/default")
    public Mono<ResponseEntity<ProductComm>> setDefaultNumber(@RequestBody Identity productCommId) {
        return this.service.setDefaultNumber(productCommId).map(ResponseEntity::ok);
    }
}
