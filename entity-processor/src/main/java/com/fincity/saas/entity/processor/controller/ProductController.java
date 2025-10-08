package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProcessorController;
import com.fincity.saas.entity.processor.dao.ProductDAO;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ProductPartnerUpdateRequest;
import com.fincity.saas.entity.processor.model.request.ProductRequest;
import com.fincity.saas.entity.processor.service.ProductService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Product>> createFromRequest(@RequestBody ProductRequest productRequest) {
        return this.service.create(productRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/for-partner")
    public Mono<ResponseEntity<Long>> updateForPartner(@RequestBody ProductPartnerUpdateRequest request) {
        return this.service.updateForPartner(request).map(ResponseEntity::ok);
    }

    @GetMapping("/internal" + PATH_ID)
    public Mono<ResponseEntity<Product>> getProductInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service
                .readIdentityWithAccess(ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null), identity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal")
    public Mono<ResponseEntity<List<Product>>> getProductsInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam List<ULong> productIds) {
        return this.service
                .getAllProducts(ProcessorAccess.of(appCode, clientCode, Boolean.TRUE, null, null), productIds)
                .map(ResponseEntity::ok);
    }
}
