package com.fincity.saas.ui.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.repository.UIFunctionDocumentRepository;
import com.fincity.saas.ui.service.UIFunctionService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/ui/functions")
public class FunctionController
        extends AbstractOverridableDataController<UIFunction, UIFunctionDocumentRepository, UIFunctionService> {

    private final Gson gson;

    public FunctionController(Gson gson) {

        this.gson = gson;
    }

    @GetMapping("/repositoryFind")
    public Mono<ResponseEntity<String>> find(@RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos, String namespace,
            String name) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
                        CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode()))),

                (ca, tup) -> this.service.getFunctionRepository(tup.getT1(), tup.getT2()),

                (ca, tup, appFunctionRepo) -> {

                    ReactiveRepository<ReactiveFunction> fRepo = (includeKIRunRepos
                            ? new ReactiveHybridRepository<ReactiveFunction>(new KIRunReactiveFunctionRepository(),
                                    appFunctionRepo)
                            : appFunctionRepo);

                    return fRepo.find(namespace, name);
                },

                (ca, tup, appFunctionRepo, fun) -> Mono.just((this.gson).toJson(fun)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.find"))
                .map(str -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(str));
    }

    @GetMapping("/repositoryFilter")
    public Mono<ResponseEntity<List<String>>> filter(@RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam(required = false, defaultValue = "") String filter) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.just(Tuples.of(CommonsUtil.nonNullValue(appCode, ca.getUrlAppCode()),
                        CommonsUtil.nonNullValue(clientCode, ca.getUrlClientCode()))),

                (ca, tup) -> this.service.getFunctionRepository(tup.getT1(), tup.getT2()),

                (ca, tup, appFunctionRepo) -> (includeKIRunRepos
                        ? new ReactiveHybridRepository<ReactiveFunction>(new KIRunReactiveFunctionRepository(),
                                appFunctionRepo)
                        : appFunctionRepo).filter(filter).collectList()

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.filter"))
                .map(ResponseEntity::ok);
    }

    // ── Surgical Function Endpoints ──────────────────────────────

    /**
     * Read specific steps from a function's definition by step name.
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/{id}/steps")
    public Mono<ResponseEntity<Map<String, Object>>> readSteps(
            @PathVariable String id,
            @RequestParam(value = "names", required = false) List<String> names) {

        return this.service.read(id)
                .map(fn -> {
                    Map<String, Object> definition = fn.getDefinition();
                    if (definition == null)
                        return ResponseEntity.ok(Map.<String, Object>of());

                    Object stepsObj = definition.get("steps");
                    if (!(stepsObj instanceof Map<?, ?> allSteps))
                        return ResponseEntity.ok(Map.<String, Object>of());

                    if (names == null || names.isEmpty())
                        return ResponseEntity.ok((Map<String, Object>) allSteps);

                    Map<String, Object> filtered = new HashMap<>();
                    for (String name : names) {
                        Object step = ((Map<String, Object>) allSteps).get(name);
                        if (step != null)
                            filtered.put(name, step);
                    }
                    return ResponseEntity.ok(filtered);
                });
    }

    /**
     * Patch steps within a function definition.
     * Uses whole-document versioning (reads current, merges steps, saves).
     */
    @SuppressWarnings("unchecked")
    @PatchMapping("/{id}/steps")
    public Mono<ResponseEntity<com.fincity.saas.ui.document.UIFunction>> patchSteps(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> stepsToMerge = (Map<String, Object>) request.getOrDefault("steps", Map.of());
        String message = (String) request.getOrDefault("message", "Step update");
        int expectedVersion = ((Number) request.getOrDefault("expectedVersion", 0)).intValue();

        return this.service.read(id)
                .flatMap(fn -> {
                    if (expectedVersion > 0 && fn.getVersion() != expectedVersion)
                        return Mono.error(new com.fincity.saas.commons.exeception.GenericException(
                                org.springframework.http.HttpStatus.PRECONDITION_FAILED, "Version mismatch"));

                    Map<String, Object> definition = fn.getDefinition();
                    if (definition == null)
                        definition = new HashMap<>();

                    Object existing = definition.get("steps");
                    Map<String, Object> currentSteps = existing instanceof Map<?, ?>
                            ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
                    currentSteps.putAll(stepsToMerge);
                    definition.put("steps", currentSteps);
                    fn.setDefinition(definition);
                    fn.setMessage(message);

                    return this.service.update(fn);
                })
                .map(ResponseEntity::ok);
    }
}
