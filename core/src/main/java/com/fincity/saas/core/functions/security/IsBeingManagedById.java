package com.fincity.saas.core.functions.security;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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

import reactor.core.publisher.Mono;

public class IsBeingManagedById extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "IsBeingManagedById";

    private static final String NAME_SPACE = "CoreServices.Security";

    private static final String EVENT_DATA_RESULT = "result";

    private static final String CLIENT_ID = "clientId";

    private static final String MANAGING_CLIENT_ID = "managingClientId";

    private final IFeignSecurityService securityService;

    public IsBeingManagedById(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_RESULT, Schema.ofBoolean(EVENT_DATA_RESULT)));

        return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)
                .setParameters(Map.of(CLIENT_ID, Parameter.of(CLIENT_ID, Schema.ofLong(CLIENT_ID)),
                        MANAGING_CLIENT_ID,
                        Parameter.of(MANAGING_CLIENT_ID, Schema.ofLong(MANAGING_CLIENT_ID))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        BigInteger clientId = context.getArguments().get(CLIENT_ID).getAsBigInteger();
        BigInteger managingClientId = context.getArguments().get(MANAGING_CLIENT_ID).getAsBigInteger();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) {
                return Mono.empty();
            }

            return this.securityService.isBeingManagedById(managingClientId, clientId)
                    .map(c -> new FunctionOutput(List.of(EventResult.outputOf(
                            Map.of(EVENT_DATA_RESULT, new JsonPrimitive(c))))));
        });
    }
}
