package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import reactor.core.publisher.Mono;

public interface IRepositoryProvider {

    Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode);

    Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode);
}
