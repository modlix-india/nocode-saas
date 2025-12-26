package com.fincity.saas.entity.processor.controller;

import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.service.ProcessorFunctionService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/entity/processor/functions")
public class ProcessorFunctionController {

    private static final String PATH = "/execute/{namespace}/{name}";
    private static final String PATH_FULL_NAME = "/execute/{name}";
    private static final String PATH_VARIABLE_NAMESPACE = "namespace";
    private static final String PATH_VARIABLE_NAME = "name";

    private final ProcessorFunctionService functionService;

    private final Gson gson;

    public ProcessorFunctionController(@Lazy ProcessorFunctionService functionService, Gson gson) {

        this.functionService = functionService;
        this.gson = gson;
    }

    @GetMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestParam("paramAppCode") String appCode,
            @RequestParam("paramClientCode") String clientCode,
            ServerHttpRequest request) {

        return this.execute(namespace, name, null, request, appCode, clientCode);
    }

    @GetMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            @RequestParam("paramAppCode") String appCode,
            @RequestParam("paramClientCode") String clientCode,
            ServerHttpRequest request) {

        Tuple2<String, String> tup = this.splitName(fullName);

        return this.execute(tup.getT1(), tup.getT2(), null, request, appCode, clientCode);
    }

    @PostMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestParam("paramAppCode") String appCode,
            @RequestParam("paramClientCode") String clientCode,
            @RequestBody String jsonString,
            ServerWebExchange exchange) {

        JsonObject job = StringUtil.safeIsBlank(jsonString) ? null : this.gson.fromJson(jsonString, JsonObject.class);

        Map<String, JsonElement> arguments =
                job == null ? null : job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return this.execute(namespace, name, arguments, exchange.getRequest(), appCode, clientCode);
    }

    @PostMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            @RequestParam("paramAppCode") String appCode,
            @RequestParam("paramClientCode") String clientCode,
            @RequestBody String jsonString,
            ServerWebExchange exchange) {

        JsonObject job = StringUtil.safeIsBlank(jsonString) ? null : this.gson.fromJson(jsonString, JsonObject.class);

        Tuple2<String, String> tup = this.splitName(fullName);

        Map<String, JsonElement> arguments =
                job == null ? null : job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return this.execute(tup.getT1(), tup.getT2(), arguments, exchange.getRequest(), appCode, clientCode);
    }

    private Tuple2<String, String> splitName(String fullName) {

        int index = fullName.lastIndexOf('.');
        String name = fullName;
        String namespace = null;
        if (index != -1) {
            namespace = fullName.substring(0, index);
            name = fullName.substring(index + 1);
        }

        return Tuples.of(namespace, name);
    }

    private Mono<ResponseEntity<String>> execute(
            String namespace,
            String name,
            Map<String, JsonElement> job,
            ServerHttpRequest request,
            String appCode,
            String clientCode) {

        return this.functionService
                .execute(namespace, name, job, request, appCode, clientCode)
                .map(FunctionOutput::allResults)
                .map(this.gson::toJson)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/repositoryFilter")
    public Mono<ResponseEntity<List<String>>> listFunctions(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam(required = false) String filter) {
        return functionService
                .getFunctionRepository(appCode, clientCode)
                .flatMapMany(repo -> repo.filter(filter != null ? filter : ""))
                .sort()
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/repositoryFind")
    public Mono<ResponseEntity<String>> findFunction(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam String namespace,
            @RequestParam String name) {
        return functionService
                .getFunctionRepository(appCode, clientCode)
                .flatMap(repo -> repo.find(namespace, name))
                .map(e -> e.getSignature())
                .map(this.gson::toJson)
                .map(ResponseEntity::ok);
    }
}
