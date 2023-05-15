package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.core.functions.CreateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

	private Map<String, ReactiveFunction> repoMap = new HashMap<>();

	private List<String> filterableNames;

	public CoreFunctionRepository(AppDataService appDataService) {

		ReactiveFunction createStorage = new CreateStorageObject(appDataService);

		repoMap.put(createStorage.getSignature()
		        .getFullName(), createStorage);

		this.filterableNames = repoMap.values()
		        .stream()
		        .map(ReactiveFunction::getSignature)
		        .map(FunctionSignature::getFullName)
		        .toList();
	}

	@Override
	public Flux<String> filter(String name) {
		return Flux.fromStream(filterableNames.stream())
		        .filter(e -> e.toLowerCase()
		                .indexOf(name.toLowerCase()) != -1);
	}

	@Override
	public Mono<ReactiveFunction> find(String namespace, String name) {

		return Mono.just(repoMap.get(namespace + "." + name));
	}

}