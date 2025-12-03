package com.fincity.saas.entity.processor.functions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class DynamicServiceFunction extends AbstractReactiveFunction {

    private static final String RESULT = "result";

    private static final Map<Class<?>, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    static {
        SCHEMA_CACHE.put(String.class, Schema.ofString("String"));
        SCHEMA_CACHE.put(Integer.class, Schema.ofInteger("Integer"));
        SCHEMA_CACHE.put(int.class, Schema.ofInteger("int"));
        SCHEMA_CACHE.put(Long.class, Schema.ofLong("Long"));
        SCHEMA_CACHE.put(long.class, Schema.ofLong("long"));
        SCHEMA_CACHE.put(Double.class, Schema.ofDouble("Double"));
        SCHEMA_CACHE.put(double.class, Schema.ofDouble("double"));
        SCHEMA_CACHE.put(Float.class, Schema.ofFloat("Float"));
        SCHEMA_CACHE.put(float.class, Schema.ofFloat("float"));
        SCHEMA_CACHE.put(Boolean.class, Schema.ofBoolean("Boolean"));
        SCHEMA_CACHE.put(boolean.class, Schema.ofBoolean("boolean"));
    }

    private final Object serviceInstance;
    private final Method method;
    private final String namespace;
    private final String functionName;
    private final Gson gson;
    private final FunctionSignature signature;

    private final ParameterMetadata[] parameterMetadata;

    public DynamicServiceFunction(
            Object serviceInstance,
            Method method,
            String namespace,
            String functionName,
            Gson gson) {
        this.serviceInstance = serviceInstance;
        this.method = method;
        this.namespace = namespace;
        this.functionName = functionName;
        this.gson = gson;
        this.method.setAccessible(true);
        this.parameterMetadata = cacheParameterMetadata();
        this.signature = buildSignature();
    }

    private ParameterMetadata[] cacheParameterMetadata() {
        java.lang.reflect.Parameter[] methodParams = method.getParameters();
        ParameterMetadata[] metadata = new ParameterMetadata[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            java.lang.reflect.Parameter param = methodParams[i];
            String paramName = param.getName();
            if (paramName == null || paramName.isEmpty()) {
                paramName = "param" + i;
            }

            metadata[i] = new ParameterMetadata(
                    paramName, param.getType(), param.getParameterizedType(), isPrimitive(param.getType()));
        }

        return metadata;
    }

    private boolean isPrimitive(Class<?> type) {
        return type.isPrimitive()
                || type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Float.class
                || type == Boolean.class
                || type == String.class;
    }

    private FunctionSignature buildSignature() {
        Map<String, Parameter> parameters = new HashMap<>();
        for (ParameterMetadata metadata : parameterMetadata) {
            Schema schema = getSchemaForType(metadata.type());
            parameters.put(
                    metadata.name(),
                    new Parameter().setParameterName(metadata.name()).setSchema(schema));
        }

        Schema returnSchema = getSchemaForReturnType();
        Event outputEvent = new Event().setName(Event.OUTPUT).setParameters(Map.of(RESULT, returnSchema));

        return new FunctionSignature()
                .setNamespace(namespace)
                .setName(functionName)
                .setParameters(parameters)
                .setEvents(Map.of(outputEvent.getName(), outputEvent));
    }

    private Schema getSchemaForType(Class<?> type) {
        // Check cache first
        Schema cached = SCHEMA_CACHE.get(type);
        if (cached != null) {
            return cached;
        }

        // Handle types with @JsonDeserialize that can accept strings
        if (type.isAnnotationPresent(JsonDeserialize.class)) {
            // Check if the deserializer can handle strings (most custom deserializers do)
            // For now, treat them as strings since they typically accept string input
            return Schema.ofString(type.getSimpleName());
        }

        // Handle complex types
        if (type.isAssignableFrom(List.class) || type.isAssignableFrom(Flux.class)) {
            return Schema.ofArray(type.getSimpleName(), Schema.ofObject("item"));
        } else if (Mono.class.isAssignableFrom(type)) {
            var monoType =
                    ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
            if (monoType instanceof Class<?>) {
                return getSchemaForType((Class<?>) monoType);
            }
            return Schema.ofObject(type.getSimpleName());
        }

        return Schema.ofObject(type.getSimpleName());
    }

    private Schema getSchemaForReturnType() {
        Class<?> returnType = method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            if (method.getGenericReturnType() instanceof ParameterizedType paramType) {
                java.lang.reflect.Type monoType = paramType.getActualTypeArguments()[0];
                if (monoType instanceof Class<?> clazz) {
                    return getSchemaForType(clazz);
                }
            }
            return Schema.ofObject("result");
        } else if (Flux.class.isAssignableFrom(returnType)) {
            return Schema.ofArray("result", Schema.ofObject("item"));
        }

        return getSchemaForType(returnType);
    }

    @Override
    public FunctionSignature getSignature() {
        return this.signature;
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        return Mono.deferContextual(cv -> FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication, ca -> executeMethod(context))
                .switchIfEmpty(Mono.defer(() -> executeMethod(context)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, functionName + ".internalExecute")));
    }

    private Mono<FunctionOutput> executeMethod(ReactiveFunctionExecutionParameters context) {
        try {
            Object[] args = prepareArguments(context);
            Object result = method.invoke(serviceInstance, args);
            return processResult(result);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to execute method: " + functionName, e));
        }
    }

    private Mono<FunctionOutput> processResult(Object result) {
        return switch (result) {
            case Mono<?> monoResult -> monoResult
                    .map(this::convertToJsonElement)
                    .map(this::createFunctionOutput);
            case Flux<?> fluxResult -> fluxResult
                    .map(this::convertToJsonElement)
                    .collectList()
                    .map(list -> createFunctionOutput(gson.toJsonTree(list)));
            case null -> Mono.just(createFunctionOutput(JsonNull.INSTANCE));
            default -> {
                JsonElement json = convertToJsonElement(result);
                yield Mono.just(createFunctionOutput(json));
            }
        };
    }

    private FunctionOutput createFunctionOutput(JsonElement json) {
        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(RESULT, json))));
    }

    private Object[] prepareArguments(ReactiveFunctionExecutionParameters context) {
        Map<String, JsonElement> arguments = context.getArguments();
        Object[] args = new Object[parameterMetadata.length];

        for (int i = 0; i < parameterMetadata.length; i++) {
            ParameterMetadata metadata = parameterMetadata[i];
            JsonElement jsonValue = arguments.get(metadata.name());

            if (jsonValue == null || jsonValue.isJsonNull()) {
                args[i] = null;
            } else {
                args[i] = convertFromJsonElement(jsonValue, metadata);
            }
        }

        return args;
    }

    private Object convertFromJsonElement(JsonElement jsonValue, ParameterMetadata metadata) {
        if (jsonValue == null || jsonValue.isJsonNull()) {
            return null;
        }

        Class<?> targetType = metadata.type();

        if (metadata.isPrimitive()) {
            return convertPrimitive(jsonValue, targetType);
        }

        // Gson will automatically use registered TypeAdapters for types with @JsonDeserialize
        // or any other registered adapters
        java.lang.reflect.Type genericType = metadata.genericType();
        return gson.fromJson(jsonValue, genericType != null ? genericType : targetType);
    }

    private Object convertPrimitive(JsonElement jsonValue, Class<?> targetType) {
        if (targetType == String.class) {
            return jsonValue.getAsString();
        } else if (targetType == Integer.class || targetType == int.class) {
            return jsonValue.getAsInt();
        } else if (targetType == Long.class || targetType == long.class) {
            return jsonValue.getAsLong();
        } else if (targetType == Double.class || targetType == double.class) {
            return jsonValue.getAsDouble();
        } else if (targetType == Float.class || targetType == float.class) {
            return jsonValue.getAsFloat();
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return jsonValue.getAsBoolean();
        }

        return null;
    }

    private JsonElement convertToJsonElement(Object result) {
        if (result == null) {
            return JsonNull.INSTANCE;
        }

        if (result instanceof IClassConvertor convertor) {
            return convertor.toJsonElement();
        }

        return gson.toJsonTree(result);
    }

    private record ParameterMetadata(
            String name, Class<?> type, java.lang.reflect.Type genericType, boolean isPrimitive) {}
}
