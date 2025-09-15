package com.fincity.saas.entity.processor.feign;

import com.fincity.saas.entity.processor.oserver.core.document.Connection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    String CONNECTION_PATH = "/api/core/connections/internal";

    @GetMapping(CONNECTION_PATH)
    Mono<Connection> getConnection(
            @RequestParam String urlClientCode,
            @RequestParam String connectionName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String connectionType);
}
