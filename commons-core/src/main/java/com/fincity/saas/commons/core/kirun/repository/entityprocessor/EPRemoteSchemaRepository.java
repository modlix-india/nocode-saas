package com.fincity.saas.commons.core.kirun.repository.entityprocessor;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;
import com.google.gson.Gson;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EPRemoteSchemaRepository implements ReactiveRepository<Schema> {

    private final IFeignEntityProcessor feignEntityProcessor;
    private final String appCode;
    private final String clientCode;
    private final Gson gson;

    public EPRemoteSchemaRepository(IFeignEntityProcessor feignEntityProcessor, String appCode, String clientCode,
            Gson gson) {
        this.appCode = appCode;
        this.clientCode = clientCode;
        this.feignEntityProcessor = feignEntityProcessor;
        this.gson = gson;
    }

    @Override
    public Mono<Schema> find(String namespace, String name) {
        return this.feignEntityProcessor.findSchema(this.appCode, this.clientCode, false, namespace, name)
                .map(str -> {
                    Schema schema = this.gson.fromJson(str, Schema.class);
                    return schema;
                });
    }

    @Override
    public Flux<String> filter(String name) {
        return this.feignEntityProcessor.filterSchemas(this.appCode, this.clientCode, false, name)
                .flatMapMany(Flux::fromIterable);
    }
}
