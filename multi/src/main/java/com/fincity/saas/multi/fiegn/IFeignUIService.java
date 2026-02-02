package com.fincity.saas.multi.fiegn;

import java.nio.ByteBuffer;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "ui")
public interface IFeignUIService {

    @PostMapping("${ui.transport:/api/ui/transports/internal/makeTransport}")
    Mono<ByteBuffer> makeTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody Map<String, Object> request);

    @PostMapping("${ui.transport.createAndApply:/api/ui/transports/internal/createAndApply}")
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

    @DeleteMapping("${ui.deleteAll:/api/ui}")
    Mono<Boolean> deleteAll(@RequestHeader("Authorization") String authorization,
                            @RequestHeader("X-Forwarded-Host") String forwardedHost,
                            @RequestHeader("X-Forwarded-Port") String forwardedPort,
                            @RequestHeader("clientCode") String clientCode,
                            @RequestHeader("appCode") String headerAppCode,
                            @RequestParam("deleteAppCode") String deleteAppCode);
}
