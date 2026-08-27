package com.fincity.saas.multi.controller;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;

import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.multi.dto.MultiApp;
import com.fincity.saas.multi.dto.MultiAppUpdate;
import com.fincity.saas.multi.service.ApplicationService;
import com.fincity.saas.multi.service.ObjectIndexService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/multi/application")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ObjectIndexService objectIndexService;

    public ApplicationController(ApplicationService applicationService,
            ObjectIndexService objectIndexService) {
        this.applicationService = applicationService;
        this.objectIndexService = objectIndexService;
    }

    /**
     * Every object an application is made of, across ui and core, as one flat
     * list. Backs the builder's object tree.
     */
    @GetMapping("/{appCode}/index")
    public Mono<ResponseEntity<Map<String, Object>>> objectIndex(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) String type) {

        return this.objectIndexService.index(authorization, forwardedHost, forwardedPort,
                headerClientCode, headerAppCode, appCode, clientCode, null, parseTypes(type))
                .map(ResponseEntity::ok);
    }

    /**
     * The same call with a name filter. Search and tree are one question, so they
     * are one code path: filtering server-side keeps an app with thousands of
     * objects from shipping all of them to satisfy a text box.
     */
    @GetMapping("/{appCode}/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchObjects(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String type) {

        return this.objectIndexService.index(authorization, forwardedHost, forwardedPort,
                headerClientCode, headerAppCode, appCode, clientCode, query, parseTypes(type))
                .map(ResponseEntity::ok);
    }

    /** `type=page,storage` to one set; blank means every type. */
    private Set<String> parseTypes(String type) {
        if (type == null || type.isBlank())
            return Set.of();
        return Arrays.stream(type.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @GetMapping("/transport")
    public Mono<Void> transport(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("appCode") String headerAppCode,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestParam String appCode, @RequestParam(required = false) String clientCode, ServerHttpResponse response) {

        return this.applicationService
                .transport(forwardedHost, forwardedPort, headerClientCode, headerAppCode, appCode, clientCode, response);

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
