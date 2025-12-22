package com.fincity.saas.commons.core.kirun.repository;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EPRemoteFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final IFeignEntityProcessor feignEntityProcessor;
    private final String appCode;
    private final String clientCode;

    public EPRemoteFunctionRepository(IFeignEntityProcessor feignEntityProcessor, String appCode, String clientCode) {
        this.appCode = appCode;
        this.clientCode = clientCode;
        this.feignEntityProcessor = feignEntityProcessor;
    }

    @Override
    public Mono<ReactiveFunction> find(String namespace, String name) {
        return this.feignEntityProcessor.findFunction(this.appCode, this.clientCode, false, namespace, name);
    }

    @Override
    public Flux<String> filter(String name) {
        return this.feignEntityProcessor.filterFunctions(this.appCode, this.clientCode, false, name);
    }
}
