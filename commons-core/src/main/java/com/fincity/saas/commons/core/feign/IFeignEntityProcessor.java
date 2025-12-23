package com.fincity.saas.commons.core.feign;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "entity-processor")
public interface IFeignEntityProcessor {

    @GetMapping("/api/entity/processor/schemas/repositoryFind")
    Mono<String> findSchema(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam String namespace,
            @RequestParam String name);

    @GetMapping("/api/entity/processor/schemas/repositoryFilter")
    Mono<List<String>> filterSchemas(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam(required = false, defaultValue = "") String filter);

    @GetMapping("/api/entity/processor/functions/repositoryFind")
    Mono<String> findFunction(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam String namespace,
            @RequestParam String name);

    @GetMapping("/api/entity/processor/functions/repositoryFilter")
    Mono<List<String>> filterFunctions(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam(required = false, defaultValue = "") String filter);

    @PostMapping("/api/entity/processor/functions/execute/{namespace}/{name}")
    Mono<String> executeFunction(
            @RequestHeader(name = "Authorization") String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam String paramAppCode,
            @RequestParam String paramClientCode,
            @RequestBody String jsonString);
}
