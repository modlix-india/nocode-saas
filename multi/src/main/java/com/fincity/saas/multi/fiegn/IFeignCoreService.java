package com.fincity.saas.multi.fiegn;

import java.nio.ByteBuffer;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    @PostMapping("${core.transport.makeTransport:/api/core/transports/makeTransport}")
    public Mono<ByteBuffer> makeTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody Map<String, Object> request);

    @PostMapping("${core.transport.createAndApply:/api/core/transports/internal/createAndApply}")
    public Mono<Map<String, Object>> createAndApplyTransport(
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

    @GetMapping("${core.connection.getOAuth2Token:/api/core/connections/oauth2/token/{connectionName}}")
    public Mono<String> getConnectionOAuth2Token(
            @RequestHeader(name = "Authorization", required = true) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("connectionName") String connectionName
    );
}