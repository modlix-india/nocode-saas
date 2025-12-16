package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ProcessorFunctionService implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private static final Map<SchemaType, java.util.function.Function<String, Number>> CONVERTOR = Map.of(
            SchemaType.DOUBLE, Double::valueOf,
            SchemaType.FLOAT, Float::valueOf,
            SchemaType.LONG, Long::valueOf,
            SchemaType.INTEGER, Integer::valueOf);

    private final Map<String, ReactiveRepository<ReactiveFunction>> functionRepositoryCache = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // No initialization needed - repositories are created lazily
    }

    /**
     * Gets the function repository for the given appCode and clientCode.
     * Similar to ProcessorSchemaService.getSchemaRepository().
     *
     * @param appCode    the application code
     * @param clientCode the client code
     * @return Mono containing the ReactiveRepository of functions
     */
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        String cacheKey = appCode + " - " + clientCode;

        ReactiveRepository<ReactiveFunction> cachedRepo = functionRepositoryCache.get(cacheKey);
        if (cachedRepo != null) {
            return Mono.just(cachedRepo);
        }

        // Lazy lookup of IRepositoryProvider beans to avoid circular dependency
        Map<String, IRepositoryProvider> repositoryProviders =
                applicationContext.getBeansOfType(IRepositoryProvider.class);

        return Flux.fromIterable(repositoryProviders.values())
                .flatMap(provider -> provider.getFunctionRepository(appCode, clientCode))
                .collectList()
                .map(repos -> {
                    @SuppressWarnings("unchecked")
                    ReactiveRepository<ReactiveFunction>[] reposArray = new ReactiveRepository[repos.size()];
                    for (int i = 0; i < repos.size(); i++) {
                        reposArray[i] = repos.get(i);
                    }
                    ReactiveRepository<ReactiveFunction> finRepo =
                            new ReactiveHybridRepository<ReactiveFunction>(reposArray);
                    functionRepositoryCache.put(cacheKey, finRepo);
                    return finRepo;
                });
    }

    public Mono<FunctionOutput> execute(
            String namespace,
            String name,
            Map<String, JsonElement> arguments,
            ServerHttpRequest request,
            String appCode,
            String clientCode) {

        return getFunctionRepository(appCode, clientCode)
                .flatMap(repository -> repository.find(namespace, name).flatMap(function -> {
                    Mono<Map<String, JsonElement>> argsMono = arguments == null
                            ? this.getRequestParamsToArguments(
                                    function.getSignature().getParameters(), request)
                            : Mono.just(arguments);
                    return argsMono.flatMap(args -> function.execute(
                            new ReactiveFunctionExecutionParameters(repository, null).setArguments(args)));
                }));
    }

    private Mono<Map<String, JsonElement>> getRequestParamsToArguments(
            Map<String, Parameter> parameters, ServerHttpRequest request) {

        MultiValueMap<String, String> queryParams =
                request == null ? new LinkedMultiValueMap<>() : request.getQueryParams();

        return Flux.fromIterable(parameters.entrySet())
                .flatMap(parameter -> {
                    List<String> value = queryParams.get(parameter.getKey());

                    if (value == null) return Mono.empty();

                    Schema schema = parameter.getValue().getSchema();
                    Type type = schema.getType();

                    Parameter param = parameter.getValue();

                    if (type.contains(SchemaType.ARRAY) || type.contains(SchemaType.OBJECT)) return Mono.empty();

                    if (type.contains(SchemaType.STRING)) return Mono.just(jsonElementString(parameter, value, param));

                    if (type.contains(SchemaType.DOUBLE)) {
                        return Mono.just(jsonElement(parameter, value, param, SchemaType.DOUBLE));
                    } else if (type.contains(SchemaType.FLOAT)) {
                        return Mono.just(jsonElement(parameter, value, param, SchemaType.FLOAT));
                    } else if (type.contains(SchemaType.LONG)) {
                        return Mono.just(jsonElement(parameter, value, param, SchemaType.LONG));
                    } else if (type.contains(SchemaType.INTEGER)) {
                        return Mono.just(jsonElement(parameter, value, param, SchemaType.INTEGER));
                    }

                    return Mono.empty();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    private Tuple2<String, JsonElement> jsonElement(
            Entry<String, Parameter> parameter, List<String> value, Parameter param, SchemaType type) {
        if (!param.isVariableArgument())
            return Tuples.of(
                    parameter.getKey(), new JsonPrimitive(CONVERTOR.get(type).apply(value.getFirst())));

        JsonArray jsonArray = new JsonArray();
        value.stream()
                .map(each -> new JsonPrimitive(CONVERTOR.get(type).apply(each)))
                .forEach(jsonArray::add);

        return Tuples.of(parameter.getKey(), jsonArray);
    }

    private Tuple2<String, JsonElement> jsonElementString(
            Entry<String, Parameter> parameter, List<String> value, Parameter param) {
        if (!param.isVariableArgument()) return Tuples.of(parameter.getKey(), new JsonPrimitive(value.getFirst()));

        JsonArray jsonArray = new JsonArray();
        value.stream().map(JsonPrimitive::new).forEach(jsonArray::add);

        return Tuples.of(parameter.getKey(), jsonArray);
    }
}
