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
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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

    private static <R> FunctionOutput toFunctionOutput(
            String resultEventName, Schema resultSchema, Gson gson, R result) {

        if (StringUtil.isNullOrBlank(resultEventName) || resultSchema == null) {
            return new FunctionOutput(List.of(EventResult.outputOf(Map.of())));
        }

        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(resultEventName, gson.toJsonTree(result)))));
    }

    public static <R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            Supplier<Mono<R>> executor) {

        return new AbstractProcessorFunction(namespaceSuffix, functionName, Map.of(), resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                return executor.get().map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result));
            }
        };
    }

    public static <P, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            SchemaUtil.ArgSpec<P> p1,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            Function<P, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());

        return new AbstractProcessorFunction(namespaceSuffix, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                P p = p1.parser().apply(gson, executionParameters.getArguments().get(p1.name()));
                return executor.apply(p).map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result));
            }
        };
    }

    public static <P1, P2, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            SchemaUtil.ArgSpec<P1> p1,
            SchemaUtil.ArgSpec<P2> p2,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            BiFunction<P1, P2, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());

        return new AbstractProcessorFunction(namespaceSuffix, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                P1 a = p1.parser()
                        .apply(gson, executionParameters.getArguments().get(p1.name()));
                P2 b = p2.parser()
                        .apply(gson, executionParameters.getArguments().get(p2.name()));
                return executor.apply(a, b)
                        .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result));
            }
        };
    }

    public static <P1, P2, P3, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            SchemaUtil.ArgSpec<P1> p1,
            SchemaUtil.ArgSpec<P2> p2,
            SchemaUtil.ArgSpec<P3> p3,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            TriFunction<P1, P2, P3, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());
        parameters.put(p3.name(), p3.parameter());

        return new AbstractProcessorFunction(namespaceSuffix, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                P1 a = p1.parser()
                        .apply(gson, executionParameters.getArguments().get(p1.name()));
                P2 b = p2.parser()
                        .apply(gson, executionParameters.getArguments().get(p2.name()));
                P3 c = p3.parser()
                        .apply(gson, executionParameters.getArguments().get(p3.name()));
                return executor.apply(a, b, c)
                        .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result));
            }
        };
    }

    public static <P1, P2, P3, P4, R> AbstractProcessorFunction createServiceFunction(
            String namespaceSuffix,
            String functionName,
            SchemaUtil.ArgSpec<P1> p1,
            SchemaUtil.ArgSpec<P2> p2,
            SchemaUtil.ArgSpec<P3> p3,
            SchemaUtil.ArgSpec<P4> p4,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            QuadFunction<P1, P2, P3, P4, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());
        parameters.put(p3.name(), p3.parameter());
        parameters.put(p4.name(), p4.parameter());

        return new AbstractProcessorFunction(namespaceSuffix, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                P1 a = p1.parser()
                        .apply(gson, executionParameters.getArguments().get(p1.name()));
                P2 b = p2.parser()
                        .apply(gson, executionParameters.getArguments().get(p2.name()));
                P3 c = p3.parser()
                        .apply(gson, executionParameters.getArguments().get(p3.name()));
                P4 d = p4.parser()
                        .apply(gson, executionParameters.getArguments().get(p4.name()));
                return executor.apply(a, b, c, d)
                        .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result));
            }
        };
    }

    @Override
    public FunctionSignature getSignature() {
        return this.functionSignature;
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface QuadFunction<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }
}
