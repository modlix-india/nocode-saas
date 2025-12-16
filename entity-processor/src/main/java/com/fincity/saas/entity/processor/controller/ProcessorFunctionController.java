package com.fincity.saas.entity.processor.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.service.ProcessorFunctionService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/entity/processor/functions/")
public class ProcessorFunctionController {

    private static final String PATH = "execute/{namespace}/{name}";
    private static final String PATH_FULL_NAME = "execute/{name}";
    private static final String PATH_VARIABLE_NAMESPACE = "namespace";
    private static final String PATH_VARIABLE_NAME = "name";

    private final ProcessorFunctionService functionService;
    private final ProcessorMessageResourceService msgService;

    private final Gson gson;

    public ProcessorFunctionController(
            @Lazy ProcessorFunctionService functionService,
            ProcessorMessageResourceService msgService,
            @Autowired Gson gson) {

        this.functionService = functionService;
        this.msgService = msgService;
        this.gson = gson;
    }

    @GetMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            ServerHttpRequest request) {

        return this.execute(namespace, name, null, request, appCode, clientCode);
    }

    @GetMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            ServerHttpRequest request) {

        Tuple2<String, String> tup = this.splitName(fullName);

        return this.execute(tup.getT1(), tup.getT2(), null, request, appCode, clientCode);
    }

    @PostMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody String jsonString,
            ServerWebExchange exchange) {

        JsonObject job = StringUtil.safeIsBlank(jsonString) ? null : this.gson.fromJson(jsonString, JsonObject.class);

        Map<String, JsonElement> arguments = job == null ? null
                : job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return this.execute(namespace, name, arguments, exchange.getRequest(), appCode, clientCode);
    }

    @PostMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody String jsonString,
            ServerWebExchange exchange) {

        JsonObject job = StringUtil.safeIsBlank(jsonString) ? null : this.gson.fromJson(jsonString, JsonObject.class);

        Tuple2<String, String> tup = this.splitName(fullName);

        Map<String, JsonElement> arguments = job == null ? null
                : job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue));

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
            String namespace, String name, Map<String, JsonElement> job, ServerHttpRequest request,
            String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                () -> this.functionService.execute(namespace, name, job, request, appCode, clientCode),
                this::extractOutputEvent)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProcessorFunctionController.execute"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND,
                        "Function",
                        namespace + "." + name));
    }

    private Mono<ResponseEntity<String>> extractOutputEvent(FunctionOutput e) {

        List<EventResult> list = new ArrayList<>();
        EventResult er;

        while ((er = e.next()) != null) {
            list.add(er);
            if (Event.OUTPUT.equals(er.getName()))
                break;
        }

        return Mono.just((this.gson).toJson(list)).map(objString -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(objString));
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
            String namespace,
            @RequestParam String name) {
        return functionService
                .getFunctionRepository(appCode, clientCode)
                .flatMap(repo -> repo.find(namespace, name))
                .map(e -> e.getSignature())
                .map(this.gson::toJson)
                .map(ResponseEntity::ok);
    }
}
