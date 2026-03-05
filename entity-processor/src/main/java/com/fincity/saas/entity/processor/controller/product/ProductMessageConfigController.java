package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.product.ProductMessageConfigDAO;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductMessageConfigsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.request.product.ProductMessageConfigRequest;
import com.fincity.saas.entity.processor.service.product.ProductMessageConfigService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/productMessageConfigs")
public class ProductMessageConfigController
        extends BaseUpdatableController<
                EntityProcessorProductMessageConfigsRecord,
                ProductMessageConfig,
                ProductMessageConfigDAO,
                ProductMessageConfigService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<List<ProductMessageConfig>>> createRequest(
            @RequestBody ProductMessageConfigRequest request) {
        return this.service.createRequest(request).map(ResponseEntity::ok);
    }

    @PostMapping(REQ_PATH + "/order")
    public Mono<ResponseEntity<List<ProductMessageConfig>>> updateOrderForGroup(
            @RequestBody List<IdAndValue<ULong, Integer>> request) {
        return this.service.updateOrderForGroup(request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/group/{id}")
    public Mono<ResponseEntity<Integer>> deleteGroup(@PathVariable("id") ULong id) {
        return this.service.deleteGroup(id).map(ResponseEntity::ok);
    }
}
