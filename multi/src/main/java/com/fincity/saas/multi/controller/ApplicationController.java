package com.fincity.saas.multi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;

import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.multi.dto.MultiApp;
import com.fincity.saas.multi.dto.MultiAppUpdate;
import com.fincity.saas.multi.service.ApplicationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/multi/application")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/transport")
    public Mono<Void> transport(
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestParam String appCode, ServerHttpResponse response) {

        return this.applicationService
            .transport(forwardedHost, forwardedPort, clientCode, headerAppCode, appCode, response);

    }

    @PostMapping
    public Mono<ResponseEntity<App>> createApplication(
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestBody MultiApp application) {

        return this.applicationService.createApplication(forwardedHost, forwardedPort, clientCode,
            headerAppCode, application).map(ResponseEntity::ok);
    }

    @PostMapping("/update")
    public Mono<ResponseEntity<Boolean>> updateApplication(
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @RequestBody MultiAppUpdate application) {

        return this.applicationService.updateApplication(forwardedHost, forwardedPort, clientCode,
            headerAppCode, application).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{appCodeOrId}")
    public Mono<ResponseEntity<Boolean>> deleteApplication(
        @RequestHeader("X-Forwarded-Host") String forwardedHost,
        @RequestHeader("X-Forwarded-Port") String forwardedPort,
        @RequestHeader("clientCode") String clientCode,
        @RequestHeader("appCode") String headerAppCode,
        @PathVariable String appCodeOrId) {

        return this.applicationService.deleteApplication(forwardedHost, forwardedPort, clientCode,
            headerAppCode, appCodeOrId).map(ResponseEntity::ok);
    }
}
