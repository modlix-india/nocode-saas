package com.fincity.saas.commons.core.functions.security;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.*;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetUserAdminEmails extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "GetUserAdminEmails";
    private static final String NAME_SPACE = "CoreServices.Security";
    private static final String APP_CODE= "appCode";
    private static final String CLIENT_CODE= "clientCode";
    private static final String EVENT_DATA_ADMIN_EMAILS="adminEmails";
    private static final String EVENT_DATA_ADD_APP="addApp";

    private final IFeignSecurityService securityService;
    private final Gson gson;


    public GetUserAdminEmails(IFeignSecurityService securityService, Gson gson) {
        this.securityService = securityService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(
                        EVENT_DATA_ADMIN_EMAILS,
                        Schema.ofString(EVENT_DATA_ADMIN_EMAILS),
                        EVENT_DATA_ADD_APP,
                        Schema.ofBoolean(EVENT_DATA_ADD_APP)));

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

        Mono<Map<String,Object>> data = securityService.getUserAdminEmailsInternal(clientCode, appCode)
                .defaultIfEmpty(Map.of("emails", List.of(), "addApp", Boolean.FALSE));;

        return data.map(result -> {

            @SuppressWarnings("unchecked")
            List<String> emails = (List<String>) result.getOrDefault("emails", List.of());
            Boolean addApp = (Boolean) result.getOrDefault("addApp", Boolean.FALSE);

            Map<String, Object> outputMap = Map.of(
                    EVENT_DATA_ADMIN_EMAILS, emails,
                    EVENT_DATA_ADD_APP, addApp);

            return new FunctionOutput(List.of(EventResult.outputOf(Map.of(
                    EVENT_DATA_ADMIN_EMAILS,
                    this.gson.toJsonTree(emails, ArrayList.class),
                    EVENT_DATA_ADD_APP,
                    new JsonPrimitive(addApp)))));
        });
    }

}
