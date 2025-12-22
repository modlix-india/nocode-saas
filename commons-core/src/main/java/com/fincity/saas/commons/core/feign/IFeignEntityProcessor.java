package com.fincity.saas.commons.core.feign;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "entity-processor")
public interface IFeignEntityProcessor {

    @GetMapping("/api/entity/processor/schemas/repositoryFind")
    Mono<Schema> findSchema(
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
    Mono<FunctionSignature> findFunction(
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
    Mono<List<EventResult>> executeFunction(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody String jsonString);
}
