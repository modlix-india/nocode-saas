package com.fincity.saas.commons.core.functions.events;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.*;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateEventFunction extends AbstractReactiveFunction {

    private static final String EVENT_OUTPUT_DATA = "created";

    private static final String FUNCTION_NAME = "CreateEvent";

    private static final String NAMESPACE = "CoreServices.Event";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String EVENT_NAME = "eventName";

    private static final String EVENT_DATA = "eventData";

    private final EventCreationService ecService;

    private final EventDefinitionService edService;

    public CreateEventFunction(EventCreationService ecService, EventDefinitionService edService) {
        this.ecService = ecService;
        this.edService = edService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_OUTPUT_DATA, Schema.ofBoolean(EVENT_OUTPUT_DATA)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(EVENT_NAME, Schema.ofString(EVENT_NAME)),
                Parameter.ofEntry(EVENT_DATA, Schema.ofObject(EVENT_DATA).setDefaultValue(new JsonObject()))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String appCode = context.getArguments().get(APP_CODE).getAsString();
        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
        String eventName = context.getArguments().get(EVENT_NAME).getAsString();
        JsonObject eventData = context.getArguments().get(EVENT_DATA).getAsJsonObject();

        Map<String, Object> data = JsonUtil.toMap(eventData);


        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY)))
                return Mono.just(new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_OUTPUT_DATA, new JsonPrimitive(false))))));

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> {
                                String inAppCode = appCode.trim().isEmpty() ? ca.getUrlAppCode() : appCode;
                                String inClientCode = clientCode.trim().isEmpty() ? ca.getClientCode() : clientCode;
                                return this.edService.read(eventName, inAppCode, inClientCode).map(ObjectWithUniqueID::getObject);
                            },

                            (ca, ed) -> this.ecService.createEvent(new EventQueObject()
                                    .setAppCode(appCode)
                                    .setClientCode(clientCode)
                                    .setEventName(eventName)
                                    .setAuthentication(ca)
                                    .setXDebug(cv.hasKey(LogUtil.DEBUG_KEY) ? cv.get(LogUtil.DEBUG_KEY) : null)
                                    .setData(data)
                            ),

                            (ca, ed, sent) -> Mono.just(new FunctionOutput(
                                    List.of(EventResult.outputOf(Map.of(EVENT_OUTPUT_DATA, new JsonPrimitive(sent))))))
                    )
                    .switchIfEmpty(Mono.defer(() -> Mono.just(new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(EVENT_OUTPUT_DATA, new JsonPrimitive(false))))))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "CreateEventFunction.internalExecute"));
        });
    }
}
