package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.product.ProductCommDAO;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductCommsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.product.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/productComms")
public class ProductCommController
        extends BaseUpdatableController<
                EntityProcessorProductCommsRecord, ProductComm, ProductCommDAO, ProductCommService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ProductComm>> createRequest(@RequestBody ProductCommRequest request) {
        return this.service.createRequest(request).map(ResponseEntity::ok);
    }

    @GetMapping("/default")
    public Mono<ResponseEntity<ProductComm>> getDefaultNumber(
            @RequestParam("productId") Identity productId,
            @RequestParam("connectionType") ConnectionType connectionType,
            @RequestParam("connectionSubType") ConnectionSubType connectionSubType) {
        return this.service
                .getDefault(productId, connectionType, connectionSubType)
                .map(ResponseEntity::ok);
    }
}
