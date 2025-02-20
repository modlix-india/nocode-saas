package com.fincity.saas.multi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.multi.service.RestUtilService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/multi/rest-util/")
public class RestUtilController {

    private final RestUtilService restUtilService;

    public RestUtilController(RestUtilService restUtilService) {
        this.restUtilService = restUtilService;
    }

    @GetMapping("meta/debug-token")
    public Mono<ResponseEntity<JsonNode>> debugMetaToken(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam String connectionName, ServerHttpResponse response) {

        return restUtilService.metaDebugToken(forwardedHost, forwardedPort, clientCode, headerAppCode,
            connectionName).map(ResponseEntity::ok);
    }

}
