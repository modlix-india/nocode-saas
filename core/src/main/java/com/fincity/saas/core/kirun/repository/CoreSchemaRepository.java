package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreSchemaRepository extends ReactiveHybridRepository<Schema> {

	private Map<String, Schema> repoMap = new HashMap<>();

	private List<String> filterableNames;

	public CoreSchemaRepository() {

		this.filterableNames = repoMap.values()
		        .stream()
		        .map(Schema::getFullName)
		        .toList();
	}

	@Override
	public Flux<String> filter(String name) {
		final String filterName = name == null ? "" : name;
		return Flux.fromStream(filterableNames.stream())
		        .filter(e -> e.toLowerCase()
		                .indexOf(filterName.toLowerCase()) != -1);
	}

	@Override
	public Mono<Schema> find(String namespace, String name) {

		return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
	}
}
