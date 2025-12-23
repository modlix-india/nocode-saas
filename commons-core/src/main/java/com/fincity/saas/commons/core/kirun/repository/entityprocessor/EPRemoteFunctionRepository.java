package com.fincity.saas.commons.core.kirun.repository.entityprocessor;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;
import com.google.gson.Gson;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EPRemoteFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final IFeignEntityProcessor feignEntityProcessor;
    private final String appCode;
    private final String clientCode;
    private final Gson gson;

    public EPRemoteFunctionRepository(IFeignEntityProcessor feignEntityProcessor, String appCode, String clientCode,
            Gson gson) {
        this.appCode = appCode;
        this.clientCode = clientCode;
        this.feignEntityProcessor = feignEntityProcessor;
        this.gson = gson;
    }

    @Override
    public Mono<ReactiveFunction> find(String namespace, String name) {
        return this.feignEntityProcessor.findFunction(this.appCode, this.clientCode, false, namespace, name)
                .map(str -> gson.fromJson(str, FunctionSignature.class))
                .map(fs -> new EPRemoteFunction(fs, this.feignEntityProcessor, this.appCode, this.clientCode,
                        this.gson));
    }

    @Override
    public Flux<String> filter(String name) {
        return this.feignEntityProcessor.filterFunctions(this.appCode, this.clientCode, false, name)
                .flatMapMany(Flux::fromIterable);
    }
}
