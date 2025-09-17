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
import com.fincity.saas.commons.core.service.NotificationService;
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
    private static final String TARGET_ID = "targetId";
    private static final String TARGET_CODE = "targetCode";
    private static final String TARGET_TYPE = "targetType";
    private static final String FILTER_AUTHORIZATION = "filterAuthorization";
    private static final String NOTIFICATION_NAME = "notificationName";
    private static final String PAYLOAD = "payload";

    private final NotificationService notificationService;

    public SendNotification(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofBoolean(EVENT_DATA)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(TARGET_ID, Schema.ofNumber(TARGET_ID).setDefaultValue(new JsonPrimitive(0))),
                Parameter.ofEntry(TARGET_CODE, Schema.ofString(TARGET_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(TARGET_TYPE, Schema.ofString(TARGET_TYPE).setDefaultValue(new JsonPrimitive(NotificationService.USER_ID))
                        .setEnums(List.of(new JsonPrimitive(NotificationService.USER_ID),
                                new JsonPrimitive(NotificationService.CLIENT_ID),
                                new JsonPrimitive(NotificationService.CLIENT_CODE)))),
                Parameter.ofEntry(FILTER_AUTHORIZATION, Schema.ofString(FILTER_AUTHORIZATION).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(NOTIFICATION_NAME, Schema.ofString(NOTIFICATION_NAME)),
                Parameter.ofEntry(PAYLOAD, Schema.ofObject(PAYLOAD).setDefaultValue(new JsonObject()))));

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
        BigInteger targetId = context.getArguments().get(TARGET_ID).getAsBigInteger();
        String targetCode = context.getArguments().get(TARGET_CODE).getAsString();
        String targetType = context.getArguments().get(TARGET_TYPE).getAsString();
        String filterAuthorization = context.getArguments().get(FILTER_AUTHORIZATION).getAsString();
        String notificationName = context.getArguments().get(NOTIFICATION_NAME).getAsString();

        Map<String, Object> payload =
                JsonUtil.toMap(context.getArguments().get(PAYLOAD).getAsJsonObject());

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY)))
                return Mono.just(new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonPrimitive(Boolean.FALSE))))));

            String inAppCode = appCode.isEmpty() ? null : appCode;
            String inClientCode = clientCode.isEmpty() ? null : clientCode;
            String inFilterAuthorization = filterAuthorization.isEmpty() ? null : filterAuthorization;
            String inTargetCode = targetCode.isEmpty() ? null : targetCode;

            return notificationService
                    .processAndSendNotification(inAppCode, inClientCode, targetId, inTargetCode, targetType, inFilterAuthorization,
                            notificationName, payload)
                    .switchIfEmpty(Mono.just(Boolean.FALSE))
                    .map(e -> new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonPrimitive(e))))));
        });
    }
}
