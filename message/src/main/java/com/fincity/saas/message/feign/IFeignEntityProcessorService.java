package com.fincity.saas.message.feign;

import com.fincity.saas.message.oserver.entity.processor.model.Product;
import java.math.BigInteger;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "entity-processor")
public interface IFeignEntityProcessorService {

    String PRODUCT_PATH = "api/entity/processor/products/internal";

    @GetMapping(PRODUCT_PATH + "/{id}")
    Mono<Product> getProductInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("id") BigInteger id);

    @GetMapping(PRODUCT_PATH)
    Mono<List<Product>> getProductsInternal(
            @RequestParam String appCode, @RequestParam String clientCode, List<BigInteger> identity);
}
