package com.fincity.saas.commons.core.functions.hash;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.util.HashUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class HashData extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "HashData";

    private static final String NAMESPACE = "CoreServices.Hash";

    private static final String DEFAULT_ALGORITHM = "SHA-256";

    private static final String PARAMETER_DATA = "data";

    private static final String PARAMETER_ALGORITHM = "algorithm";

    private static final String PARAMETER_PRIMITIVE_LEVEL = "primitiveLevel";

    private static final String EVENT_RESULT_NAME = "result";

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(EVENT_RESULT_NAME, Schema.ofString(EVENT_RESULT_NAME)));

        Map<String, Parameter> parameters = Map.ofEntries(
                Parameter.ofEntry(PARAMETER_DATA, Schema.ofAny(PARAMETER_DATA)),
                Parameter.ofEntry(
                        PARAMETER_ALGORITHM,
                        Schema.ofString(PARAMETER_ALGORITHM)
                                .setEnums(List.of(
                                        new JsonPrimitive("SHA-256"),
                                        new JsonPrimitive("SHA-384"),
                                        new JsonPrimitive("SHA-512"),
                                        new JsonPrimitive("MD5"),
                                        new JsonPrimitive("MD2"),
                                        new JsonPrimitive("MD4"),
                                        new JsonPrimitive("SHA-1")))
                                .setDefaultValue(new JsonPrimitive(DEFAULT_ALGORITHM))),
                Parameter.ofEntry(
                        PARAMETER_PRIMITIVE_LEVEL,
                        Schema.ofBoolean(PARAMETER_PRIMITIVE_LEVEL).setDefaultValue(new JsonPrimitive(false))));

        return new FunctionSignature()
                .setName(FUNCTION_NAME)
                .setNamespace(NAMESPACE)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        Map<String, JsonElement> args = context.getArguments();

        String algorithm = args.get(PARAMETER_ALGORITHM).getAsString();

        JsonElement data = args.get(PARAMETER_DATA);

        boolean primitiveLevel = args.get(PARAMETER_PRIMITIVE_LEVEL).getAsBoolean();

        JsonElement hashValue = processElement(data, algorithm, primitiveLevel);

        return Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT_NAME, hashValue)))));
    }

    private JsonElement processElement(JsonElement element, String algorithm, boolean primitiveLevel) {
        if (element.isJsonNull()) {
            return HashUtil.createHash(JsonNull.INSTANCE, algorithm);
        }
        if (!primitiveLevel) {
            return HashUtil.createHash(element.toString(), algorithm);
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                Number number = primitive.getAsNumber();
                return HashUtil.createHash(number.toString(), algorithm);
            } else if (primitive.isBoolean()) {
                return HashUtil.createHash(primitive.getAsBoolean(), algorithm);
            }
            return HashUtil.createHash(primitive.toString(), algorithm);
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            JsonArray hashedArray = new JsonArray();
            array.forEach(e -> hashedArray.add(processElement(e, algorithm, true)));
            return hashedArray;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonObject hashedObject = new JsonObject();
            object.entrySet().forEach(entry -> {
                hashedObject.add(
                        processElement(new JsonPrimitive(entry.getKey()), algorithm, true)
                                .getAsString(),
                        processElement(entry.getValue(), algorithm, true));
            });
            return hashedObject;
        }

        return element;
    }
}
