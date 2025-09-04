package com.fincity.saas.commons.core.mq.services;

import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventCallFunctionService implements IEventActionService {

    private final CoreFunctionService functionService;
    private final CoreMessageResourceService msgService;
    private final Gson gson;

    public EventCallFunctionService(CoreFunctionService functionService, CoreMessageResourceService msgService, Gson gson) {
        this.functionService = functionService;
        this.msgService = msgService;
        this.gson = gson;
    }

    @Override
    public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

        Map<String, Object> data = CommonsUtil.nonNullValue(queObject.getData(), Map.of());
        JsonObject job = gson.toJsonTree(data).getAsJsonObject();

        Map<String, Object> taskParameter = CommonsUtil.nonNullValue(task.getParameters(), Map.of());

        String namespace = StringUtil.safeValueOf(taskParameter.get("namespace"));
        String name = StringUtil.safeValueOf(taskParameter.get("name"));
        String functionParameterName = StringUtil.safeValueOf(taskParameter.get("functionParameterName"));

        if (StringUtil.safeIsBlank(namespace) || StringUtil.safeIsBlank(name))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg), CoreMessageResourceService.UNABLE_TO_EXECUTE, name, namespace, job);

        Map<String, JsonElement> params = new HashMap<>();

        if (!StringUtil.safeIsBlank(functionParameterName)) {
            params.put(functionParameterName, job);
        }

        return this.execute(namespace, name, queObject.getAppCode(), queObject.getClientCode(), params);
    }

    private Mono<Boolean> execute(
            String namespace,
            String name,
            String appCode,
            String clientCode,
            Map<String, JsonElement> job) {

        return FlatMapUtil.flatMapMono(
                        () -> this.functionService.execute(namespace, name, appCode, clientCode, job, null),
                        this::extractOutputEvent)
                .map(e -> true)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventCallFunctionService.execute"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        "Function",
                        namespace + "." + name));
    }

    public Mono<ResponseEntity<String>> extractOutputEvent(FunctionOutput e) {

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
