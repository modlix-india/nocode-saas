package com.fincity.saas.core.functions.security;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class GetClient extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "GetClient";

    private static final String NAME_SPACE = "CoreServices.Security";

    private static final String EVENT_DATA_CLIENT = "client";

    private static final String ID_CODE_PARAM = "idOrCode";

    private final IFeignSecurityService securityService;
    private final Gson gson;

    public GetClient(IFeignSecurityService securityService, Gson gson) {
        this.securityService = securityService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event().setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_DATA_CLIENT, Schema.ofObject(EVENT_DATA_CLIENT)
                        .setProperties(Map.of(
                                "id", Schema.ofLong("id"),
                                "createdBy", Schema.ofLong("createdBy"),
                                "updatedBy", Schema.ofLong("updatedBy"),
                                "code", Schema.ofString("code"),
                                "name", Schema.ofString("name"),
                                "typeCode", Schema.ofString("typeCode"),
                                "tokenValidityMinutes", Schema.ofInteger("tokenValidityMinutes"),
                                "localeCode", Schema.ofString("localeCode"),
                                "statusCode", Schema.ofString("statusCode"),
                                "businessType", Schema.ofString("businessType")))));

        return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)
                .setParameters(Map.of(
                        ID_CODE_PARAM,
                        Parameter.of(ID_CODE_PARAM,
                                new Schema().setName(ID_CODE_PARAM)
                                        .setType(Type.of(SchemaType.STRING, SchemaType.LONG)))))
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        JsonPrimitive param = context.getArguments().get(ID_CODE_PARAM).getAsJsonPrimitive();

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) {
                return Mono.empty();
            }

            Mono<Client> client = param.isString() ? this.securityService.getClientByCode(param.getAsString())
                    : this.securityService.getClientById(BigInteger.valueOf(param.getAsLong()));

            return client.map(c -> new FunctionOutput(List.of(EventResult.outputOf(
                    Map.of(EVENT_DATA_CLIENT, this.gson.toJsonTree(c, Client.class))))));
        });
    }
}
