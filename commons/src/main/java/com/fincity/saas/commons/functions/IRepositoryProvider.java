package com.fincity.saas.commons.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.repository.MapSchemaRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public interface IRepositoryProvider {

    Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode);

    Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode);

    default Mono<ReactiveRepository<Schema>> defaultSchemaRepositoryFor(Class<?> dtoClass, ClassSchema classSchema) {

        Map<String, Schema> schemas = new HashMap<>();

        Logger logger = LoggerFactory.getLogger(this.getClass());

        try {
            String namespace = classSchema.getNamespaceForClass(dtoClass);
            String name = dtoClass.getSimpleName();

            Schema schema = classSchema.generateSchemaForClass(dtoClass);
            if (schema != null) {
                schemas.put(namespace + "." + name, schema);
                logger.info("Generated schema for {} using DTO {}.{}", this.getClass().getSimpleName(), namespace, name);
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to generate schema in {} for DTO {}: {}",
                    this.getClass().getName(),
                    dtoClass.getName(),
                    e.getMessage(),
                    e);
        }

        if (!schemas.isEmpty()) {
            return Mono.just(new MapSchemaRepository(schemas));
        }

        return Mono.empty();
    }
}
