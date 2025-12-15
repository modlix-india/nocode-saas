package com.fincity.saas.entity.processor.functions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import static java.lang.reflect.Modifier.STATIC;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

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
        SCHEMA_CACHE.put(ULong.class, Schema.ofLong("ULong"));
        SCHEMA_CACHE.put(UInteger.class, Schema.ofInteger("UInteger"));
        SCHEMA_CACHE.put(UShort.class, Schema.ofInteger("UShort"));
        SCHEMA_CACHE.put(UByte.class, Schema.ofInteger("UByte"));
    }

    private final Method method;
    private final MethodHandle methodHandle;
    private final String functionName;
    private final Gson gson;
    private final FunctionSignature signature;
    private final Map<String, Schema> schemaMap;

    private final String[] parameterNames;
    private final Function<JsonElement, Object>[] argumentConverters;

    public DynamicServiceFunction(
            Object serviceInstance, Method method, String namespace, String functionName, Gson gson) {
        this(serviceInstance, method, namespace, functionName, gson, null);
    }

    public DynamicServiceFunction(
            Object serviceInstance,
            Method method,
            String namespace,
            String functionName,
            Gson gson,
            Map<String, Schema> schemaMap) {

        this.method = method;
        this.functionName = functionName;
        this.gson = gson;
        this.schemaMap = schemaMap;

        if (!this.method.canAccess(serviceInstance))
            this.method.setAccessible(true);

        try {
            MethodHandle unboundHandle = MethodHandles.publicLookup().unreflect(method);
            this.methodHandle = (method.getModifiers() & STATIC) == 0 ? unboundHandle.bindTo(serviceInstance)
                    : unboundHandle;
        } catch (IllegalAccessException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not create MethodHandle for " + functionName, e);
        }

        var params = this.method.getParameters();
        this.parameterNames = new String[params.length];

        @SuppressWarnings("unchecked")
        Function<JsonElement, Object>[] converters = new Function[params.length];

        this.argumentConverters = converters;

        for (int i = 0; i < params.length; i++) {
            var param = params[i];
            String pName = param.getName();
            this.parameterNames[i] = (pName == null || pName.isEmpty()) ? PARAM_PREFIX + i : pName;
            this.argumentConverters[i] = this.createArgumentConverter(param.getType(), param.getParameterizedType());
        }

        this.signature = this.buildSignature(namespace, params);
    }

    private Function<JsonElement, Object> createArgumentConverter(Class<?> type, Type genericType) {

        if (PRIMITIVE_CONVERTERS.containsKey(type))
            return this.createPrimitiveConverter(type);

        if (MultiValueMap.class.isAssignableFrom(type))
            return this.createMultiValueMapConverter();

        // TODO: Add convertors for method arguments if needed

        return this.createGenericConverter(genericType, type);
    }

    private Function<JsonElement, Object> createPrimitiveConverter(Class<?> type) {

        Function<JsonElement, Object> primitiveConverter = PRIMITIVE_CONVERTERS.get(type);
        return json -> (json == null || json.isJsonNull()) ? null : primitiveConverter.apply(json);
    }

    private Function<JsonElement, Object> createMultiValueMapConverter() {

        return json -> {
            if (json == null || json.isJsonNull())
                return null;
            Object mapObj = this.gson.fromJson(json, Map.class);
            return mapObj instanceof Map<?, ?> map ? this.convertMapToMultiValueMap(map) : mapObj;
        };
    }

    private MultiValueMap<String, String> convertMapToMultiValueMap(Map<?, ?> map) {
        if (map == null || map.isEmpty())
            return new LinkedMultiValueMap<>(0);

        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>(map.size());

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null)
                this.processEntry(multiValueMap, entry.getKey().toString(), entry.getValue());
        }

        return multiValueMap;
    }

    private void processEntry(MultiValueMap<String, String> map, String key, Object value) {
        switch (value) {
            case null -> map.add(key, null);
            case List<?> list -> map.put(key, this.convertToStringList(list));
            case Object[] array -> map.put(key, this.convertToStringList(Arrays.asList(array)));
            default -> map.add(key, value.toString());
        }
    }

    private List<String> convertToStringList(List<?> items) {
        return items.stream()
                .map(item -> item != null ? item.toString() : null)
                .collect(Collectors.toCollection(() -> new ArrayList<>(items.size())));
    }

    private Function<JsonElement, Object> createGenericConverter(Type genericType, Class<?> type) {

        Type targetType = genericType != null ? genericType : type;
        return json -> (json == null || json.isJsonNull()) ? null : this.gson.fromJson(json, targetType);
    }

    private FunctionSignature buildSignature(String namespace, java.lang.reflect.Parameter[] params) {
        Map<String, Parameter> parameters = HashMap.newHashMap(params.length);

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            Schema schema = this.getSchemaForType(type);
            parameters.put(
                    this.parameterNames[i],
                    new Parameter().setParameterName(this.parameterNames[i]).setSchema(schema));
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
        // First check if we have a schema in the provided schema map
        if (this.schemaMap != null) {
            // Try to find schema by class name in different namespaces
            String className = type.getSimpleName();
            for (Map.Entry<String, Schema> entry : this.schemaMap.entrySet()) {
                String fullName = entry.getKey();
                if (fullName.endsWith("." + className)) {
                    return entry.getValue();
                }
            }
        }

        return SCHEMA_CACHE.computeIfAbsent(type, t -> {
            if (List.class.isAssignableFrom(t) || Flux.class.isAssignableFrom(t))
                return Schema.ofArray(t.getSimpleName(), Schema.ofObject(ITEM));

            if (Mono.class.isAssignableFrom(t)) {
                Type genericReturnType = this.method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType paramType) {
                    Type monoType = paramType.getActualTypeArguments()[0];
                    if (monoType instanceof Class<?> clazz)
                        return this.getSchemaForType(clazz);
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
                if (monoType instanceof Class<?> clazz)
                    return this.getSchemaForType(clazz);
            }
            return Schema.ofObject(RESULT);
        }

        if (Flux.class.isAssignableFrom(returnType))
            return Schema.ofArray(RESULT, Schema.ofObject(ITEM));

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
                .contextWrite(Context.of(LogUtil.METHOD_NAME, this.functionName + ".internalExecute")));
    }

    private Mono<FunctionOutput> executeMethod(ReactiveFunctionExecutionParameters context) {
        try {
            Object[] args = this.prepareArgumentsFromContext(context);
            return this.processResult(this.invokeMethod(args));
        } catch (GenericException e) {
            return Mono.error(e);
        } catch (Exception e) {
            return Mono.error(new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute method: " + this.functionName, e));
        }
    }

    private Object[] prepareArgumentsFromContext(ReactiveFunctionExecutionParameters context) {
        Object[] args = new Object[this.argumentConverters.length];
        Map<String, JsonElement> arguments = context.getArguments();

        for (int i = 0; i < this.argumentConverters.length; i++) {
            JsonElement jsonValue = arguments.get(this.parameterNames[i]);
            args[i] = this.argumentConverters[i].apply(jsonValue);
        }

        return args;
    }

    private Object invokeMethod(Object[] args) {
        try {
            return this.methodHandle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to invoke method " + this.functionName + " via MethodHandle",
                    t);
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

    private JsonElement convertToJsonElement(Object result) {
        if (result == null)
            return JsonNull.INSTANCE;

        if (result instanceof IClassConvertor convertor)
            return convertor.toJsonElement();

        return this.gson.toJsonTree(result);
    }
}
