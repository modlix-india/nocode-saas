package com.fincity.saas.commons.core.functions.securitycontext;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class HasAuthority extends AbstractReactiveFunction {

    private static final String AUTHORITY_PARAM = "authority";

    private static final String FUNCTION_NAME = "HasAuthority";

    private static final String NAME_SPACE = "CoreServices.SecurityContext";

    private static final String EVENT_DATA_RESULT = "result";

    private final ContextService userContextService;

    public HasAuthority(ContextService userContextService) {
        this.userContextService = userContextService;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_RESULT, Schema.ofBoolean(EVENT_DATA_RESULT)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setParameters(Map.of(AUTHORITY_PARAM, Parameter.of(AUTHORITY_PARAM, Schema.ofString(AUTHORITY_PARAM))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String authority = context.getArguments().get(AUTHORITY_PARAM).getAsString();

        return userContextService
                .hasAuthority(authority)
                .map(hasAuthority -> new FunctionOutput(
                        List.of(EventResult.outputOf(Map.of(EVENT_DATA_RESULT, new JsonPrimitive(hasAuthority))))));
    }
}
