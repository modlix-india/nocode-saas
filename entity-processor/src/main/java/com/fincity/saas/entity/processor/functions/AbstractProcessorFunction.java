package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public abstract class AbstractProcessorFunction extends AbstractReactiveFunction {

    public static final String NAMESPACE_PREFIX = "EntityProcessor";

    private final FunctionSignature functionSignature;

    protected AbstractProcessorFunction(
            String namespaceSuffix,
            String functionName,
            Map<String, Parameter> parameters,
            String resultEventName,
            Schema resultSchema) {

        Event resultEvent = new Event().setName(Event.OUTPUT);

        if (!StringUtil.isNullOrBlank(resultEventName)) {
            resultEvent.setParameters(Map.of(resultEventName, resultSchema));
        }
        this.functionSignature = new FunctionSignature()
                .setNamespace(NAMESPACE_PREFIX + "." + namespaceSuffix)
                .setName(functionName)
                .setParameters(parameters)
                .setEvents(Map.of(Event.OUTPUT, resultEvent));
    }

    public static <T> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            Map<String, Parameter> parameters,
            String resultEventName,
            Schema resultSchema,
            T service,
            BiFunction<ReactiveFunctionExecutionParameters, T, Mono<FunctionOutput>> executor) {

        return new AbstractProcessorFunction(namespaceSuffix, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                return executor.apply(executionParameters, service);
            }
        };
    }

    public static <T, P, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            Map<String, Parameter> parameters,
            String resultEventName,
            Schema resultSchema,
            String paramName,
            Class<P> paramClass,
            Gson gson,
            T service,
            Function<P, Mono<R>> executor) {

        return createServiceFunction(
                namespaceSuffix,
                functionName,
                parameters,
                resultEventName,
                resultSchema,
                service,
                (executionParameters, svc) -> {
                    JsonElement paramJson = executionParameters.getArguments().get(paramName);
                    P param = gson.fromJson(paramJson, paramClass);
                    return executor.apply(param)
                            .map(result -> new FunctionOutput(
                                    List.of(EventResult.outputOf(Map.of(resultEventName, gson.toJsonTree(result))))));
                });
    }

    public static <T, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            Map<String, Parameter> parameters,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            T service,
            Function<T, Mono<R>> executor) {

        return createServiceFunction(
                namespaceSuffix,
                functionName,
                parameters,
                resultEventName,
                resultSchema,
                service,
                (executionParameters, svc) -> executor.apply(svc).map(result -> {
                    return new FunctionOutput(
                            List.of(EventResult.outputOf(Map.of(resultEventName, gson.toJsonTree(result)))));
                }));
    }

    @Override
    public FunctionSignature getSignature() {
        return this.functionSignature;
    }
}
