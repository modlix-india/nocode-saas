package com.fincity.saas.commons.core.functions.security;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class IsBeingManagedByCode extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "IsBeingManagedByCode";

    private static final String NAME_SPACE = "CoreServices.Security";

    private static final String EVENT_DATA_RESULT = "result";

    private static final String CLIENT_CODE = "clientCode";

    private static final String MANAGING_CLIENT_CODE = "managingClientCode";

    private final IFeignSecurityService securityService;

    public IsBeingManagedByCode(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_RESULT, Schema.ofBoolean(EVENT_DATA_RESULT)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(
                        CLIENT_CODE,
                        Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE)),
                        MANAGING_CLIENT_CODE,
                        Parameter.of(MANAGING_CLIENT_CODE, Schema.ofString(MANAGING_CLIENT_CODE))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
        String managingClientCode =
                context.getArguments().get(MANAGING_CLIENT_CODE).getAsString();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) {
                return Mono.empty();
            }

            return this.securityService
                    .isBeingManaged(managingClientCode, clientCode)
                    .map(c -> new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(EVENT_DATA_RESULT, new JsonPrimitive(c))))));
        });
    }
}
