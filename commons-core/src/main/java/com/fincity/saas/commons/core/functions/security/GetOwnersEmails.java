package com.fincity.saas.commons.core.functions.security;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.*;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.google.gson.Gson;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns the email ids of every ROLE_Owner user of the (clientCode, appCode) in
 * context. Mirrors {@link GetUserAdminEmails} but resolves strictly the owners
 * (via security's /users/internal/ownerEmails -> ClientService.getOwnersEmails).
 */
public class GetOwnersEmails extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "GetOwnersEmails";
    private static final String NAME_SPACE = "CoreServices.Security";
    private static final String APP_CODE = "appCode";
    private static final String CLIENT_CODE = "clientCode";
    private static final String EVENT_DATA_OWNER_EMAILS = "ownerEmails";

    private final IFeignSecurityService securityService;
    private final Gson gson;

    public GetOwnersEmails(IFeignSecurityService securityService, Gson gson) {
        this.securityService = securityService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_OWNER_EMAILS, Schema.ofString(EVENT_DATA_OWNER_EMAILS)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE)),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE))));

        return new FunctionSignature().setName(FUNCTION_NAME).setNamespace(NAME_SPACE)
                .setParameters(parameters).setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String appCode = context.getArguments().get(APP_CODE).getAsString();
        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();

        return securityService.getOwnersEmailsInternal(clientCode, appCode)
                .defaultIfEmpty(Map.of("emails", List.of()))
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    List<String> emails = (List<String>) result.getOrDefault("emails", List.of());
                    return new FunctionOutput(List.of(EventResult.outputOf(Map.of(
                            EVENT_DATA_OWNER_EMAILS,
                            this.gson.toJsonTree(emails, ArrayList.class)))));
                });
    }
}
