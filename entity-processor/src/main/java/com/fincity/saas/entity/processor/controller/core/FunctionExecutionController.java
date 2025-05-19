package com.fincity.saas.entity.processor.controller.core;

import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/entity/processor/core/function/")
public class FunctionExecutionController {

    private static final String PATH = "execute/{namespace}/{name}";
    private static final String PATH_FULL_NAME = "execute/{name}";
    private static final String PATH_VARIABLE_NAMESPACE = "namespace";
    private static final String PATH_VARIABLE_NAME = "name";

    private final CoreFunctionService functionService;
    private final CoreMessageResourceService msgService;

    private final Gson gson;

    public FunctionExecutionController(
            CoreFunctionService functionService, CoreMessageResourceService msgService, Gson gson) {

        this.functionService = functionService;
        this.msgService = msgService;
        this.gson = gson;
    }

    @GetMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            ServerHttpRequest request) {

        return this.execute(namespace, name, appCode, clientCode, null, request);
    }

    @GetMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            ServerHttpRequest request) {

        Tuple2<String, String> tup = this.splitName(fullName);

        return this.execute(tup.getT1(), tup.getT2(), appCode, clientCode, null, request);
    }

    @PostMapping(PATH)
    public Mono<ResponseEntity<String>> executeWith(
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestBody String jsonString) {

        JsonObject job = StringUtil.safeIsBlank(jsonString)
                ? new JsonObject()
                : this.gson.fromJson(jsonString, JsonObject.class);

        return this.execute(
                namespace,
                name,
                appCode,
                clientCode,
                job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue)),
                null);
    }

    @PostMapping(PATH_FULL_NAME)
    public Mono<ResponseEntity<String>> executeWith(
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @PathVariable(PATH_VARIABLE_NAME) String fullName,
            @RequestBody String jsonString) {

        JsonObject job = StringUtil.safeIsBlank(jsonString)
                ? new JsonObject()
                : this.gson.fromJson(jsonString, JsonObject.class);

        Tuple2<String, String> tup = this.splitName(fullName);

        return this.execute(
                tup.getT1(),
                tup.getT2(),
                appCode,
                clientCode,
                job.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue)),
                null);
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
            String appCode,
            String clientCode,
            Map<String, JsonElement> job,
            ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(
                        () -> this.functionService.execute(namespace, name, appCode, clientCode, job, request),
                        this::extractOutputEvent)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionExecutionController.execute"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        "Function",
                        namespace + "." + name));
    }

    private Mono<ResponseEntity<String>> extractOutputEvent(FunctionOutput e) {

        List<EventResult> list = new ArrayList<>();
        EventResult er;

        while ((er = e.next()) != null) {
            list.add(er);
            if (Event.OUTPUT.equals(er.getName())) break;
        }

        return Mono.just((this.gson).toJson(list)).map(objString -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(objString));
    }
}
