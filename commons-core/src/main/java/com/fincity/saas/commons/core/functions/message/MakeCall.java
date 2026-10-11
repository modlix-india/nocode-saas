package com.fincity.saas.commons.core.functions.message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.feign.IFeignMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Places an outbound call through the tenant's call connection.
 *
 * <p>The route entity-processor places deal calls through, with the call owned by {@code core}: its
 * status callbacks come back as the core event {@code CALL_EVENT}, keyed on the provider's call id
 * returned here as {@code call.sid}.
 *
 * <p>No check of the number dialled, and the message service makes none either. The calling
 * function must take it from a record the caller may act on, never straight from the page, or any
 * user can ring any number on the tenant's account.
 */
public class MakeCall extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "MakeCall";
    private static final String NAMESPACE = "CoreServices.Message";

    static final String OWNER_SERVICE = "core";

    static final String APP_CODE = "appCode";
    static final String CLIENT_CODE = "clientCode";
    static final String TO_NUMBER = "toNumber";
    static final String TO_COUNTRY_CODE = "toCountryCode";
    static final String CALLER_ID = "callerId";
    static final String CALLER_ID_COUNTRY_CODE = "callerIdCountryCode";
    static final String CONNECTION_NAME = "connectionName";

    static final String EVENT_CALL = "call";

    private final IFeignMessageService messageService;
    private final IFeignSecurityService securityService;
    private final Gson gson;

    public MakeCall(IFeignMessageService messageService, IFeignSecurityService securityService, Gson gson) {
        this.messageService = messageService;
        this.securityService = securityService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_CALL, Schema.ofObject(EVENT_CALL)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Map.entry(APP_CODE, Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive("")))),
                Map.entry(CLIENT_CODE, Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))),
                Map.entry(TO_NUMBER, Parameter.of(TO_NUMBER, Schema.ofString(TO_NUMBER))),
                Map.entry(TO_COUNTRY_CODE, Parameter.of(TO_COUNTRY_CODE, Schema.ofInteger(TO_COUNTRY_CODE).setDefaultValue(new JsonPrimitive(0)))),
                Map.entry(CALLER_ID, Parameter.of(CALLER_ID, Schema.ofString(CALLER_ID).setDefaultValue(new JsonPrimitive("")))),
                Map.entry(CALLER_ID_COUNTRY_CODE, Parameter.of(CALLER_ID_COUNTRY_CODE, Schema.ofInteger(CALLER_ID_COUNTRY_CODE).setDefaultValue(new JsonPrimitive(0)))),
                Map.entry(CONNECTION_NAME, Parameter.of(CONNECTION_NAME, Schema.ofString(CONNECTION_NAME).setDefaultValue(new JsonPrimitive(""))))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        Map<String, JsonElement> args = context.getArguments();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) return Mono.empty();

            if (StringUtil.safeIsBlank(SendWhatsapp.string(args, TO_NUMBER)))
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "toNumber is required"));

            return FlatMapUtil.flatMapMono(
                            () -> MessageTenant.resolve(
                                    this.securityService,
                                    SendWhatsapp.string(args, APP_CODE),
                                    SendWhatsapp.string(args, CLIENT_CODE)),
                            tenant -> this.messageService.makeCallInternal(
                                    tenant.getT1(), tenant.getT2(), OWNER_SERVICE, request(args)),
                            (tenant, call) -> Mono.just(new FunctionOutput(List.of(
                                    EventResult.outputOf(Map.of(EVENT_CALL, this.gson.toJsonTree(call)))))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "MakeCall.internalExecute"));
        });
    }

    /**
     * The message service's {@code CallRequest}: phone numbers as {countryCode, number}, which is the
     * only shape its deserialiser reads. A country code of 0 is left out so it applies its default.
     */
    static Map<String, Object> request(Map<String, JsonElement> args) {

        Map<String, Object> request = new HashMap<>();
        request.put(TO_NUMBER, phone(args, TO_COUNTRY_CODE, TO_NUMBER));

        if (!StringUtil.safeIsBlank(SendWhatsapp.string(args, CALLER_ID)))
            request.put(CALLER_ID, phone(args, CALLER_ID_COUNTRY_CODE, CALLER_ID));

        String connectionName = SendWhatsapp.string(args, CONNECTION_NAME);
        if (!StringUtil.safeIsBlank(connectionName)) request.put(CONNECTION_NAME, connectionName);

        return request;
    }

    private static Map<String, Object> phone(Map<String, JsonElement> args, String countryKey, String numberKey) {
        Map<String, Object> phone = new HashMap<>();
        phone.put("number", SendWhatsapp.string(args, numberKey));
        JsonElement country = args.get(countryKey);
        if (country != null && country.isJsonPrimitive() && country.getAsInt() > 0)
            phone.put("countryCode", country.getAsInt());
        return phone;
    }
}
