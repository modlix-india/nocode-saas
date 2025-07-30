package com.fincity.saas.entity.collector.fiegn;

import org.springframework.web.bind.annotation.*;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    @GetMapping("${core.connection.getOAuth2Token:/api/core/connections/oauth2/token/{connectionName}}")
    Mono<String> getConnectionOAuth2Token(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(name = "X-Forwarded-Port", required = false) String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("connectionName") String connectionName);
}
