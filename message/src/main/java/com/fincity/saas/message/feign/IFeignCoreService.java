package com.fincity.saas.message.feign;

import com.fincity.saas.message.oserver.core.document.Connection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping(CONNECTION_PATH + "/oauth2/token/{connectionName}")
    Mono<String> getConnectionOAuth2Token(
            @RequestHeader(name = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(name = "X-Forwarded-Port", required = false) String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("connectionName") String connectionName);
}
