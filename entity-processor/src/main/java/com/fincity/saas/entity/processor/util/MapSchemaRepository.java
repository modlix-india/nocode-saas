package com.fincity.saas.entity.processor.util;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MapSchemaRepository implements ReactiveRepository<Schema> {
    private final Map<String, Schema> schemaMap;
    private final List<String> filterableNames;

    public MapSchemaRepository(Map<String, Schema> schemaMap) {
        this.schemaMap = schemaMap;
        this.filterableNames = schemaMap.keySet().stream().toList();
    }

    @Override
    public Mono<Schema> find(String namespace, String name) {
        return Mono.justOrEmpty(schemaMap.get(namespace + "." + name));
    }

    @Override
    public Flux<String> filter(String name) {
        return Flux.fromIterable(filterableNames).filter(fullName -> fullName.contains(name));
    }
}
