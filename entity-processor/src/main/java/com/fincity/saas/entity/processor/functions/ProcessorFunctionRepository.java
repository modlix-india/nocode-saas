package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProcessorFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final Map<String, ReactiveFunction> repoMap = new HashMap<>();
    private final List<String> filterableNames;

    public ProcessorFunctionRepository(ProcessorFunctionRepositoryBuilder builder) {
        ServiceFunctionGenerator generator = new ServiceFunctionGenerator(builder.gson, builder.messageService);

        if (builder.services != null) {
            for (Object service : builder.services) {
                if (service != null) {
                    List<ReactiveFunction> functions = generator.generateFunctions(service);
                    for (ReactiveFunction function : functions) {
                        String fullName = function.getSignature().getFullName();
                        repoMap.putIfAbsent(fullName, function);
                    }
                }
            }
        }

        this.filterableNames = repoMap.values().stream()
                .map(ReactiveFunction::getSignature)
                .map(FunctionSignature::getFullName)
                .toList();
    }

    @Override
    public Flux<String> filter(String name) {
        final String filterName = name == null ? "" : name;
        return Flux.fromStream(filterableNames.stream())
                .filter(e -> e.toLowerCase().contains(filterName.toLowerCase()));
    }

    @Override
    public Mono<ReactiveFunction> find(String namespace, String name) {
        return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
    }

    @Data
    @Accessors(chain = true)
    public static class ProcessorFunctionRepositoryBuilder {
        private List<Object> services;
        private Gson gson;
        private ProcessorMessageResourceService messageService;
    }
}
