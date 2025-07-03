package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products")
public class ProductController
        extends BaseProcessorController<EntityProcessorProductsRecord, Product, ProductDAO, ProductService> {

    public static final String CX_APP = "cx-app";

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Product>> createFromRequest(@RequestBody ProductRequest productRequest) {
        return this.service.create(productRequest).map(ResponseEntity::ok);
    }

    @GetMapping(CX_APP)
    public Mono<ResponseEntity<BaseResponse>> readForCxApp(@RequestParam String cxAppToken) {
        return this.service.readForCxApp(cxAppToken).map(ResponseEntity::ok);
    }
}
