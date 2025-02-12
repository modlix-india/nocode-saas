package com.fincity.saas.multi.fiegn;

import java.nio.ByteBuffer;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    @PostMapping("${core.transport.makeTransport:/api/core/transports/makeTransport}")
    Mono<ByteBuffer> makeTransport(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestBody Map<String, Object> request);

    @PostMapping("${core.transport.createAndApply:/api/core/transports/internal/createAndApply}")
    Mono<Map<String, Object>> createAndApplyTransport(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestParam Boolean isJson,
        @RequestParam Boolean isForBaseApp,
        @RequestParam String applicationCode,
        @RequestParam String fileName,
        @RequestBody ByteBuffer file);

    @DeleteMapping("${core.deleteAll:/api/core}")
    Mono<Boolean> deleteAll(@RequestHeader("Authorization") String authorization,
                            @RequestHeader("X-Forwarded-Host") String forwardedHost,
                            @RequestHeader("X-Forwarded-Port") String forwardedPort,
                            @RequestHeader("clientCode") String clientCode,
                            @RequestHeader("appCode") String headerAppCode,
                            @RequestParam("deleteAppCode") String deleteAppCode);
}

    @GetMapping("${core.connection.getOAuth2Token:/api/core/connections/oauth2/token/{connectionName}}")
    Mono<String> getConnectionOAuth2Token(
            @RequestHeader(name = "Authorization", required = true) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("connectionName") String connectionName
    );
}