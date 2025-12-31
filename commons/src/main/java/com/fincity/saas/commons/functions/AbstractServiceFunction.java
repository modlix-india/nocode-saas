package com.fincity.saas.commons.functions;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public abstract class AbstractServiceFunction extends AbstractReactiveFunction {

    public static final String NAMESPACE_PREFIX = "EntityProcessor";
    private static final String ERROR_EVENT_NAME = "error";
    private static final ClassSchema classSchema = ClassSchema.getInstance();

    private final FunctionSignature functionSignature;

    protected AbstractServiceFunction(
            String namespace,
            String functionName,
            Map<String, Parameter> parameters,
            String resultEventName,
            Schema resultSchema) {

        Event resultEvent = new Event().setName(Event.OUTPUT);

        if (!StringUtil.isNullOrBlank(resultEventName)) {
            resultEvent.setParameters(Map.of(resultEventName, resultSchema));
        }

        Schema errorSchema = createErrorSchema();
        Event errorEvent = new Event()
                .setName(Event.ERROR)
                .setParameters(Map.of(ERROR_EVENT_NAME, errorSchema));

        Map<String, Event> events = new LinkedHashMap<>();
        events.put(Event.OUTPUT, resultEvent);
        events.put(Event.ERROR, errorEvent);

        this.functionSignature = new FunctionSignature()
                .setNamespace(namespace)
                .setName(functionName)
                .setParameters(parameters)
                .setEvents(events);
    }

    private static Schema createErrorSchema() {
        return classSchema.generateSchemaForClass(GenericException.GenericExceptionData.class);
    }

    private static <R> FunctionOutput toFunctionOutput(
            String resultEventName, Schema resultSchema, Gson gson, R result) {

        if (StringUtil.isNullOrBlank(resultEventName) || resultSchema == null) {
            return new FunctionOutput(List.of(EventResult.outputOf(Map.of())));
        }

        return new FunctionOutput(List.of(EventResult.outputOf(Map.of(resultEventName, gson.toJsonTree(result)))));
    }

    private static FunctionOutput toErrorOutput(Gson gson, Throwable throwable) {
        GenericException.GenericExceptionData errorData;
        
        if (throwable instanceof GenericException genericException) {
            errorData = genericException.toExceptionData();
        } else {
            String exceptionId = GenericException.uniqueId();
            errorData = new GenericException.GenericExceptionData()
                    .setExceptionId(exceptionId)
                    .setMessage(throwable.getMessage() != null ? throwable.getMessage() : "An unexpected error occurred")
                    .setStackTrace(getStackTraceAsString(throwable))
                    .setDebugMessage(throwable.getMessage());
        }

        return new FunctionOutput(List.of(
                EventResult.of(Event.ERROR, Map.of(ERROR_EVENT_NAME, gson.toJsonTree(errorData)))));
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("---------- Cause ").append(cause.getMessage()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append(element.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    public static <R> AbstractServiceFunction createServiceFunction(
            String namespace,
            String functionName,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            Supplier<Mono<R>> executor) {

        return new AbstractServiceFunction(namespace, functionName, Map.of(), resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                return executor.get()
                        .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result))
                        .onErrorResume(throwable -> Mono.just(toErrorOutput(gson, throwable)));
            }
        };
    }

    public static <P, R> AbstractServiceFunction createServiceFunction(
            String namespace,
            String functionName,
            ClassSchema.ArgSpec<P> p1,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            Function<P, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());

        return new AbstractServiceFunction(namespace, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                try {
                    P p = p1.parser().apply(gson, executionParameters.getArguments().get(p1.name()));
                    return executor.apply(p)
                            .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result))
                            .onErrorResume(throwable -> Mono.just(toErrorOutput(gson, throwable)));
                } catch (Throwable throwable) {
                    return Mono.just(toErrorOutput(gson, throwable));
                }
            }
        };
    }

    public static <P1, P2, R> AbstractServiceFunction createServiceFunction( // NOSONAR
            String namespace,
            String functionName,
            ClassSchema.ArgSpec<P1> p1,
            ClassSchema.ArgSpec<P2> p2,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            BiFunction<P1, P2, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());

        return new AbstractServiceFunction(namespace, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                try {
                    P1 a = p1.parser()
                            .apply(gson, executionParameters.getArguments().get(p1.name()));
                    P2 b = p2.parser()
                            .apply(gson, executionParameters.getArguments().get(p2.name()));
                    return executor.apply(a, b)
                            .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result))
                            .onErrorResume(throwable -> Mono.just(toErrorOutput(gson, throwable)));
                } catch (Throwable throwable) {
                    return Mono.just(toErrorOutput(gson, throwable));
                }
            }
        };
    }

    public static <P1, P2, P3, R> AbstractServiceFunction createServiceFunction( // NOSONAR
            String namespace,
            String functionName,
            ClassSchema.ArgSpec<P1> p1,
            ClassSchema.ArgSpec<P2> p2,
            ClassSchema.ArgSpec<P3> p3,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            TriFunction<P1, P2, P3, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());
        parameters.put(p3.name(), p3.parameter());

        return new AbstractServiceFunction(namespace, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                try {
                    P1 a = p1.parser()
                            .apply(gson, executionParameters.getArguments().get(p1.name()));
                    P2 b = p2.parser()
                            .apply(gson, executionParameters.getArguments().get(p2.name()));
                    P3 c = p3.parser()
                            .apply(gson, executionParameters.getArguments().get(p3.name()));
                    return executor.apply(a, b, c)
                            .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result))
                            .onErrorResume(throwable -> Mono.just(toErrorOutput(gson, throwable)));
                } catch (Throwable throwable) {
                    return Mono.just(toErrorOutput(gson, throwable));
                }
            }
        };
    }

    public static <P1, P2, P3, P4, R> AbstractServiceFunction createServiceFunction( // NOSONAR
            String namespace,
            String functionName,
            ClassSchema.ArgSpec<P1> p1,
            ClassSchema.ArgSpec<P2> p2,
            ClassSchema.ArgSpec<P3> p3,
            ClassSchema.ArgSpec<P4> p4,
            String resultEventName,
            Schema resultSchema,
            Gson gson,
            QuadFunction<P1, P2, P3, P4, Mono<R>> executor) {

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put(p1.name(), p1.parameter());
        parameters.put(p2.name(), p2.parameter());
        parameters.put(p3.name(), p3.parameter());
        parameters.put(p4.name(), p4.parameter());

        return new AbstractServiceFunction(namespace, functionName, parameters, resultEventName, resultSchema) {
            @Override
            protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters executionParameters) {
                try {
                    P1 a = p1.parser()
                            .apply(gson, executionParameters.getArguments().get(p1.name()));
                    P2 b = p2.parser()
                            .apply(gson, executionParameters.getArguments().get(p2.name()));
                    P3 c = p3.parser()
                            .apply(gson, executionParameters.getArguments().get(p3.name()));
                    P4 d = p4.parser()
                            .apply(gson, executionParameters.getArguments().get(p4.name()));
                    return executor.apply(a, b, c, d)
                            .map(result -> toFunctionOutput(resultEventName, resultSchema, gson, result))
                            .onErrorResume(throwable -> Mono.just(toErrorOutput(gson, throwable)));
                } catch (Throwable throwable) {
                    return Mono.just(toErrorOutput(gson, throwable));
                }
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
