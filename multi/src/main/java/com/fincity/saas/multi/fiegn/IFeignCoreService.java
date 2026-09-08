package com.fincity.saas.multi.fiegn;

import java.nio.ByteBuffer;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    @PostMapping("${core.transport.makeTransport:/api/core/transports/internal/makeTransport}")
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


    /**
     * Reactive Feign flattens headers, path variables AND query params into a
     * single name-keyed map, so no two arguments may share a name. This method
     * already carries an {@code appCode} HEADER (the calling app's context), so
     * the target app rides as {@code targetAppCode} and the target client as
     * {@code forClientCode}. Name either of them {@code appCode} /
     * {@code clientCode} and the call dies with "Duplicate key appCode" before
     * it leaves the process; the server side still binds them as
     * {@code appCode} / {@code clientCode} from its own mapping.
     */
    @GetMapping("${core.index:/api/core/index/internal/{targetAppCode}}")
    Mono<Map<String, Object>> objectIndex(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("targetAppCode") String appCode,
            @RequestParam(name = "forClientCode", required = false) String forClientCode);

}
