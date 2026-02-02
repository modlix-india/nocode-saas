package com.fincity.saas.commons.core.mq.services;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EventCallFunctionService implements IEventActionService {

    private final Logger logger = LoggerFactory.getLogger(EventCallFunctionService.class);

    private final CoreFunctionService functionService;
    private final CoreMessageResourceService msgService;
    private final Gson gson;

    public EventCallFunctionService(CoreFunctionService functionService, CoreMessageResourceService msgService,
            Gson gson) {
        this.functionService = functionService;
        this.msgService = msgService;
        this.gson = gson;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

        Map<String, Object> data = CommonsUtil.nonNullValue(queObject.getData(), Map.of());
        JsonObject job = gson.toJsonTree(data).getAsJsonObject();

        Map<String, Object> taskParameter = CommonsUtil.nonNullValue(task.getParameters(), Map.of());

        String namespace = StringUtil.safeValueOf(taskParameter.get("namespace"));
        String name = StringUtil.safeValueOf(taskParameter.get("name"));
        String functionParameterName = StringUtil.safeValueOf(taskParameter.get("functionParameterName"));

        if (StringUtil.safeIsBlank(namespace) || StringUtil.safeIsBlank(name))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.UNABLE_TO_EXECUTE, name, namespace, job);

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

        logger.info("Executing function: {} in namespace: {} with job: {}", name, namespace, job);

        return this.functionService.execute(namespace, name, appCode, clientCode, job, null)
                .map(e -> true)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        "Function",
                        namespace + "." + name))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventCallFunctionService.execute"));
    }
}
