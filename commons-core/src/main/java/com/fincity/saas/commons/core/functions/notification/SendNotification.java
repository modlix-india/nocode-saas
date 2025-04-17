package com.fincity.saas.commons.core.functions.notification;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.saas.commons.core.service.notification.NotificationProcessingService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class SendNotification extends AbstractReactiveFunction {

    private static final String EVENT_DATA = "sent";
    private static final String FUNCTION_NAME = "SendNotification";
    private static final String NAMESPACE = "CoreServices.Notification";
    private static final String APP_CODE = "appCode";
    private static final String CLIENT_CODE = "clientCode";
    private static final String USER_ID = "userId";
    private static final String NOTIFICATION_NAME = "notificationName";
    private static final String OBJECT_MAP = "objectMap";

    private final NotificationProcessingService notificationService;

    public SendNotification(NotificationProcessingService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofBoolean(EVENT_DATA)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(USER_ID, Schema.ofNumber(USER_ID)),
                Parameter.ofEntry(NOTIFICATION_NAME, Schema.ofString(NOTIFICATION_NAME)),
                Parameter.ofEntry(OBJECT_MAP, Schema.ofObject(OBJECT_MAP).setDefaultValue(new JsonObject()))));

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
        BigInteger userId = context.getArguments().get(USER_ID).getAsBigInteger();
        String notificationName = context.getArguments().get(NOTIFICATION_NAME).getAsString();

        Map<String, Object> objectMap =
                JsonUtil.toMap(context.getArguments().get(OBJECT_MAP).getAsJsonObject());

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY)))
                return Mono.just(new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonPrimitive(Boolean.FALSE))))));

            return notificationService
                    .processAndSendNotification(appCode, clientCode, userId, notificationName, objectMap)
                    .switchIfEmpty(Mono.just(Boolean.FALSE))
                    .map(e -> new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonPrimitive(e))))));
        });
    }
}
