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
import com.fincity.saas.entity.processor.functions.ProcessorFunctionRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final AtomicReference<ReactiveHybridRepository<ReactiveFunction>> processorFunctionRepository =
            new AtomicReference<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private ApplicationContext applicationContext;

    @Autowired
    private Gson gson;

    @Autowired
    private ProcessorMessageResourceService messageService;

    private static final Map<SchemaType, java.util.function.Function<String, Number>> CONVERTOR = Map.of(
            SchemaType.DOUBLE, Double::valueOf,
            SchemaType.FLOAT, Float::valueOf,
            SchemaType.LONG, Long::valueOf,
            SchemaType.INTEGER, Integer::valueOf);

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only initialize once, and only if this is the root application context
        // (to avoid double initialization in web apps with parent/child contexts)
        if (event.getApplicationContext().getParent() == null && initialized.compareAndSet(false, true)) {
            init();
        }
    }

    private void init() {
        // Collect all service beans from the entity.processor.service package
        List<Object> services = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            // Only process beans that:
            // 1. Are not ProcessorMessageResourceService or ProcessorFunctionService
            // 2. Are annotated with @Service
            // 3. Are in entity.processor.service package but not in base subpackage
            if (!(bean instanceof ProcessorMessageResourceService || bean instanceof ProcessorFunctionService)
                    && beanClass.isAnnotationPresent(Service.class)) {
                String packageName =
                        beanClass.getPackage() != null ? beanClass.getPackage().getName() : "";
                if (packageName.contains("entity.processor.service") && !packageName.contains("base")) {
                    services.add(bean);
                }
            }
        }

        ReactiveHybridRepository<ReactiveFunction> repository = new ReactiveHybridRepository<>(
                new ProcessorFunctionRepository(new ProcessorFunctionRepository.ProcessorFunctionRepositoryBuilder()
                        .setServices(services)
                        .setGson(gson)
                        .setMessageService(messageService)));
        this.processorFunctionRepository.set(repository);
    }

    public ReactiveRepository<ReactiveFunction> getFunctionRepository() {
        ReactiveHybridRepository<ReactiveFunction> repository = processorFunctionRepository.get();
        if (repository == null) {
            throw new IllegalStateException("ProcessorFunctionRepository has not been initialized yet");
        }
        return repository;
    }

    public Mono<FunctionOutput> execute(
            String namespace, String name, Map<String, JsonElement> arguments, ServerHttpRequest request) {
        ReactiveHybridRepository<ReactiveFunction> repository = processorFunctionRepository.get();
        if (repository == null) {
            return Mono.error(new IllegalStateException("ProcessorFunctionRepository has not been initialized yet"));
        }
        return repository.find(namespace, name).flatMap(function -> {
            Mono<Map<String, JsonElement>> argsMono = arguments == null
                    ? getRequestParamsToArguments(function.getSignature().getParameters(), request)
                    : Mono.just(arguments);
            return argsMono.flatMap(args ->
                    function.execute(new ReactiveFunctionExecutionParameters(repository, null).setArguments(args)));
        });
    }

    private Mono<Map<String, JsonElement>> getRequestParamsToArguments(
            Map<String, Parameter> parameters, ServerHttpRequest request) {

        MultiValueMap<String, String> queryParams =
                request == null ? new LinkedMultiValueMap<>() : request.getQueryParams();

        return Flux.fromIterable(parameters.entrySet())
                .flatMap(e -> {
                    List<String> value = queryParams.get(e.getKey());

                    if (value == null) return Mono.empty();

                    Schema schema = e.getValue().getSchema();
                    Type type = schema.getType();

                    Parameter param = e.getValue();

                    if (type.contains(SchemaType.ARRAY) || type.contains(SchemaType.OBJECT)) return Mono.empty();

                    if (type.contains(SchemaType.STRING)) return Mono.just(jsonElementString(e, value, param));

                    if (type.contains(SchemaType.DOUBLE)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.DOUBLE));
                    } else if (type.contains(SchemaType.FLOAT)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.FLOAT));
                    } else if (type.contains(SchemaType.LONG)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.LONG));
                    } else if (type.contains(SchemaType.INTEGER)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.INTEGER));
                    }

                    return Mono.empty();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    private Tuple2<String, JsonElement> jsonElement(
            Entry<String, Parameter> e, List<String> value, Parameter param, SchemaType type) {
        if (!param.isVariableArgument())
            return Tuples.of(e.getKey(), new JsonPrimitive(CONVERTOR.get(type).apply(value.getFirst())));

        JsonArray jsonArray = new JsonArray();
        value.stream()
                .map(each -> new JsonPrimitive(CONVERTOR.get(type).apply(each)))
                .forEach(jsonArray::add);

        return Tuples.of(e.getKey(), jsonArray);
    }

    private Tuple2<String, JsonElement> jsonElementString(
            Entry<String, Parameter> e, List<String> value, Parameter param) {
        if (!param.isVariableArgument()) return Tuples.of(e.getKey(), new JsonPrimitive(value.getFirst()));

        JsonArray jsonArray = new JsonArray();
        value.stream().map(JsonPrimitive::new).forEach(jsonArray::add);

        return Tuples.of(e.getKey(), jsonArray);
    }
}
