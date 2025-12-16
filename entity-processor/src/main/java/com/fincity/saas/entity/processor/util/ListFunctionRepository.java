package com.fincity.saas.entity.processor.util;

import java.util.List;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ListFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final List<ReactiveFunction> functions;

    public ListFunctionRepository(List<ReactiveFunction> functions) {
        this.functions = functions;
    }

    @Override
    public Flux<String> filter(String name) {
        return Flux.fromIterable(functions).map(function -> function.getSignature().getFullName())
                .filter(fullName -> fullName.contains(name));
    }

    @Override
    public Mono<ReactiveFunction> find(String namespace, String name) {
        return Mono.justOrEmpty(functions.stream()
                .filter(function -> function.getSignature().getFullName().equals(namespace + "." + name)).findFirst());
    }
}
