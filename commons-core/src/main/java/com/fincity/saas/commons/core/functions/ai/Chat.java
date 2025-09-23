package com.fincity.saas.commons.core.functions.ai;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.*;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.connection.ai.AIService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonPrimitive;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chat extends AbstractReactiveFunction {

    private static final String EVENT_RESPONSE = "response";

    private static final String ERROR_MESSAGE = "error";

    private static final String FUNCTION_NAME = "Chat";

    private static final String NAMESPACE = "CoreServices.AI";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String CONNECTION_NAME = "connectionName";

    private static final String PROMPT = "prompt";

    private final AIService aiService;

    public Chat(AIService aiService) {
        this.aiService = aiService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESPONSE, Schema.ofString(EVENT_RESPONSE)));
        Event errorEvent = new Event().setName(Event.ERROR).setParameters(Map.of(ERROR_MESSAGE, Schema.ofString(ERROR_MESSAGE)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CONNECTION_NAME, Schema.ofString(CONNECTION_NAME)),
                Parameter.ofEntry(PROMPT, Schema.ofString(PROMPT))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(errorEvent.getName(), errorEvent, event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String appCode = context.getArguments().get(APP_CODE).getAsString();
        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
        String connectionName = context.getArguments().get(CONNECTION_NAME).getAsString();
        String prompt = context.getArguments().get(PROMPT).getAsString();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY)))
                return Mono.just(new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_RESPONSE, new JsonPrimitive(false))))));

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> {
                                String inAppCode = appCode.trim().isEmpty() ? ca.getUrlAppCode() : appCode;
                                String inClientCode = clientCode.trim().isEmpty() ? ca.getClientCode() : clientCode;

                                Mono<String> response = aiService.chat(inAppCode, inClientCode, connectionName, prompt);

                                return response.map(e -> new FunctionOutput(
                                        List.of(EventResult.outputOf(Map.of(EVENT_RESPONSE, new JsonPrimitive(e))))));
                            })
                    .onErrorResume(err -> Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR, Map.of(ERROR_MESSAGE, new JsonPrimitive(err.getMessage()))),
                            EventResult.outputOf(Map.of(EVENT_RESPONSE, new JsonPrimitive(""))))))
                    )
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "Chat.internalExecute"));
        });
    }
}
