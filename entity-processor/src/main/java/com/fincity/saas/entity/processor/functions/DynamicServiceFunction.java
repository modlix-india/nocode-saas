package com.fincity.saas.entity.processor.functions;

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
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class DynamicServiceFunction extends AbstractReactiveFunction {

    private static final String RESULT = "result";
    private static final String ITEM = "item";
    private static final String PARAM_PREFIX = "param";

    private static final Map<Class<?>, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Function<JsonElement, Object>> PRIMITIVE_CONVERTERS = Map.ofEntries(
            Map.entry(String.class, JsonElement::getAsString),
            Map.entry(Integer.class, JsonElement::getAsInt),
            Map.entry(int.class, JsonElement::getAsInt),
            Map.entry(Long.class, JsonElement::getAsLong),
            Map.entry(long.class, JsonElement::getAsLong),
            Map.entry(Double.class, JsonElement::getAsDouble),
            Map.entry(double.class, JsonElement::getAsDouble),
            Map.entry(Float.class, JsonElement::getAsFloat),
            Map.entry(float.class, JsonElement::getAsFloat),
            Map.entry(Boolean.class, JsonElement::getAsBoolean),
            Map.entry(boolean.class, JsonElement::getAsBoolean));

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
        SCHEMA_CACHE.put(java.time.LocalDateTime.class, Schema.ofString("LocalDateTime"));
        SCHEMA_CACHE.put(java.time.LocalDate.class, Schema.ofString("LocalDate"));

        // JOOQ unsigned number types
        SCHEMA_CACHE.put(ULong.class, Schema.ofLong("ULong"));
        SCHEMA_CACHE.put(UInteger.class, Schema.ofInteger("UInteger"));
        SCHEMA_CACHE.put(UShort.class, Schema.ofInteger("UShort"));
        SCHEMA_CACHE.put(UByte.class, Schema.ofInteger("UByte"));
    }

    private final Object serviceInstance;
    private final Method method;
    private final String functionName;
    private final Gson gson;
    private final FunctionSignature signature;
    private final ParameterMetadata[] parameterMetadata;

    private final Context logContext;

    public DynamicServiceFunction(
            Object serviceInstance, Method method, String namespace, String functionName, Gson gson) {
        this.serviceInstance = serviceInstance;
        this.method = method;
        this.functionName = functionName;
        this.gson = gson;
        this.method.setAccessible(true);

        this.parameterMetadata = cacheParameterMetadata();
        this.signature = buildSignature(namespace);
        this.logContext = Context.of(LogUtil.METHOD_NAME, functionName + ".internalExecute");
    }

    private ParameterMetadata[] cacheParameterMetadata() {
        var methodParams = this.method.getParameters();
        ParameterMetadata[] metadata = new ParameterMetadata[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            var param = methodParams[i];
            String paramName = param.getName();
            if (paramName == null || paramName.isEmpty()) paramName = PARAM_PREFIX + i;

            Class<?> paramType = param.getType();
            metadata[i] = new ParameterMetadata(
                    paramName, paramType, param.getParameterizedType(), PRIMITIVE_CONVERTERS.containsKey(paramType));
        }

        return metadata;
    }

    private FunctionSignature buildSignature(String namespace) {
        Map<String, Parameter> parameters = HashMap.newHashMap(this.parameterMetadata.length);
        for (ParameterMetadata metadata : this.parameterMetadata) {
            Schema schema = this.getSchemaForType(metadata.type());
            parameters.put(
                    metadata.name(),
                    new Parameter().setParameterName(metadata.name()).setSchema(schema));
        }

        Schema returnSchema = this.getSchemaForReturnType();
        Event outputEvent = new Event().setName(Event.OUTPUT).setParameters(Map.of(RESULT, returnSchema));

        return new FunctionSignature()
                .setNamespace(namespace)
                .setName(this.functionName)
                .setParameters(parameters)
                .setEvents(Map.of(outputEvent.getName(), outputEvent));
    }

    private Schema getSchemaForType(Class<?> type) {

        return SCHEMA_CACHE.computeIfAbsent(type, t -> {
            if (List.class.isAssignableFrom(t) || Flux.class.isAssignableFrom(t))
                return Schema.ofArray(t.getSimpleName(), Schema.ofObject(ITEM));

            if (Mono.class.isAssignableFrom(t)) {
                Type genericReturnType = this.method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType paramType) {
                    Type monoType = paramType.getActualTypeArguments()[0];
                    if (monoType instanceof Class<?> clazz) return this.getSchemaForType(clazz);
                }
            }

            return Schema.ofObject(t.getSimpleName());
        });
    }

    private Schema getSchemaForReturnType() {
        Class<?> returnType = this.method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            if (this.method.getGenericReturnType() instanceof ParameterizedType paramType) {
                Type monoType = paramType.getActualTypeArguments()[0];
                if (monoType instanceof Class<?> clazz) return this.getSchemaForType(clazz);
            }
            return Schema.ofObject(RESULT);
        }

        if (Flux.class.isAssignableFrom(returnType)) return Schema.ofArray(RESULT, Schema.ofObject(ITEM));

        return this.getSchemaForType(returnType);
    }

    @Override
    public FunctionSignature getSignature() {
        return this.signature;
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        return Mono.deferContextual(cv -> FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication, ca -> this.executeMethod(context))
                .switchIfEmpty(Mono.defer(() -> this.executeMethod(context)))
                .contextWrite(this.logContext));
    }

    private Mono<FunctionOutput> executeMethod(ReactiveFunctionExecutionParameters context) {
        try {
            Object[] args = this.prepareArguments(context);
            Object result = this.method.invoke(this.serviceInstance, args);
            return this.processResult(result);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to execute method: " + this.functionName, e));
        }
    }

    private Mono<FunctionOutput> processResult(Object result) {
        return switch (result) {
            case null -> Mono.just(this.createFunctionOutput(JsonNull.INSTANCE));
            case Mono<?> monoResult ->
                monoResult.map(this::convertToJsonElement).map(this::createFunctionOutput);
            case Flux<?> fluxResult ->
                fluxResult
                        .map(this::convertToJsonElement)
                        .collectList()
                        .map(list -> this.createFunctionOutput(this.gson.toJsonTree(list)));
            default -> Mono.just(this.createFunctionOutput(this.convertToJsonElement(result)));
        };
    }

    private FunctionOutput createFunctionOutput(JsonElement json) {
        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(RESULT, json))));
    }

    private Object[] prepareArguments(ReactiveFunctionExecutionParameters context) {
        Map<String, JsonElement> arguments = context.getArguments();
        Object[] args = new Object[this.parameterMetadata.length];

        for (int i = 0; i < this.parameterMetadata.length; i++) {
            ParameterMetadata metadata = this.parameterMetadata[i];
            JsonElement jsonValue = arguments.get(metadata.name());
            args[i] = (jsonValue == null || jsonValue.isJsonNull())
                    ? null
                    : this.convertFromJsonElement(jsonValue, metadata);
        }

        return args;
    }

    private Object convertFromJsonElement(JsonElement jsonValue, ParameterMetadata metadata) {
        if (jsonValue == null || jsonValue.isJsonNull()) return null;

        if (metadata.isPrimitive()) {
            Function<JsonElement, Object> converter = PRIMITIVE_CONVERTERS.get(metadata.type());
            return converter != null ? converter.apply(jsonValue) : null;
        }

        Type genericType = metadata.genericType();
        return this.gson.fromJson(jsonValue, genericType != null ? genericType : metadata.type());
    }

    private JsonElement convertToJsonElement(Object result) {
        if (result == null) return JsonNull.INSTANCE;

        if (result instanceof IClassConvertor convertor) return convertor.toJsonElement();

        return this.gson.toJsonTree(result);
    }

    private record ParameterMetadata(String name, Class<?> type, Type genericType, boolean isPrimitive) {}
}
