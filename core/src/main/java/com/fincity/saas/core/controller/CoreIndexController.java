package com.fincity.saas.core.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.core.service.CoreIndexService;

import reactor.core.publisher.Mono;

/**
 * Core's application index: every core object an app owns, as name / id /
 * version, in one call. The counterpart to
 * {@code GET /api/ui/applications/{appCode}/index}.
 */
@RestController
@RequestMapping("api/core/index")
public class CoreIndexController {

    private final CoreIndexService indexService;

    public CoreIndexController(CoreIndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getIndex(
            @RequestParam String appCode,
            @RequestParam(required = false) String clientCode) {

        return this.indexService.buildIndex(appCode, clientCode)
                .map(ResponseEntity::ok);
    }

    /**
     * Service-to-service variant, called by {@code multi}.
     *
     * <p>
     * appCode rides in the PATH, and the client code is named
     * {@code forClientCode}, because reactive Feign flattens headers and query
     * params into one map: a method carrying both an {@code appCode} header and
     * an {@code appCode} query param dies with "Duplicate key appCode" before it
     * ever leaves the caller.
     */
    @GetMapping("/internal/{appCode}")
    public Mono<ResponseEntity<Map<String, Object>>> getInternalIndex(
            @PathVariable String appCode,
            @RequestParam(name = "forClientCode", required = false) String clientCode) {

        return this.indexService.buildIndex(appCode, clientCode)
                .map(ResponseEntity::ok);
    }
}
