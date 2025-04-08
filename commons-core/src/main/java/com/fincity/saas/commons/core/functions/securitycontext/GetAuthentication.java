package com.fincity.saas.commons.core.functions.securitycontext;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class GetAuthentication extends AbstractReactiveFunction {
    private static final String FUNCTION_NAME = "GetAuthentication";

    private static final String NAME_SPACE = "CoreServices.SecurityContext";

    private static final String EVENT_DATA_AUTHENTICATION = "auth";

    private final ContextService userContextService;
    private final Gson gson;

    public GetAuthentication(ContextService userContextService, Gson gson) {
        this.userContextService = userContextService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(
                        EVENT_DATA_AUTHENTICATION,
                        Schema.ofRef(CoreSchemaRepository.SCHEMA_NAMESPACE_SECURITY_CONTEXT + "."
                                + CoreSchemaRepository.SCHEMA_NAME_CONTEXT_AUTHENTICATION)));

        return new FunctionSignature()
                .setNamespace(NAME_SPACE)
                .setName(FUNCTION_NAME)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        return userContextService.getUsersContextAuthentication().map(contextAuthentication -> {
            JsonObject authJob = this.gson
                    .toJsonTree(contextAuthentication, ContextAuthentication.class)
                    .getAsJsonObject();

            authJob.remove("accessTokenExpiryAt");
            authJob.remove("accessToken");

            return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_DATA_AUTHENTICATION, authJob))));
        });
    }
}
