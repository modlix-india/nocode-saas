package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.core.functions.rest.DeleteRequest;
import com.fincity.saas.core.functions.rest.GetRequest;
import com.fincity.saas.core.functions.rest.PatchRequest;
import com.fincity.saas.core.functions.rest.PostRequest;
import com.fincity.saas.core.functions.rest.PutRequest;
import com.fincity.saas.core.functions.storage.CreateStorageObject;
import com.fincity.saas.core.functions.storage.DeleteStorageObject;
import com.fincity.saas.core.functions.storage.ReadPageStorageObject;
import com.fincity.saas.core.functions.storage.ReadStorageObject;
import com.fincity.saas.core.functions.storage.UpdateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.fincity.saas.core.service.connection.rest.RestService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

	private Map<String, ReactiveFunction> repoMap = new HashMap<>();

	private List<String> filterableNames;

	public CoreFunctionRepository(AppDataService appDataService, ObjectMapper objectMapper, RestService restService) {

		ReactiveFunction createStorage = new CreateStorageObject(appDataService);

		ReactiveFunction deleteStorage = new DeleteStorageObject(appDataService);

		ReactiveFunction updateStorage = new UpdateStorageObject(appDataService);

		ReactiveFunction readStorage = new ReadStorageObject(appDataService);

		ReactiveFunction readPageStorage = new ReadPageStorageObject(appDataService, objectMapper);

		ReactiveFunction getRequest = new GetRequest(restService);

		ReactiveFunction postRequest = new PostRequest(restService);

		ReactiveFunction putRequest = new PutRequest(restService);

		ReactiveFunction patchRequest = new PatchRequest(restService);

		ReactiveFunction deleteRequest = new DeleteRequest(restService);

		repoMap.put(createStorage.getSignature().getFullName(), createStorage);

		repoMap.put(deleteStorage.getSignature().getFullName(), deleteStorage);

		repoMap.put(updateStorage.getSignature().getFullName(), updateStorage);

		repoMap.put(readStorage.getSignature().getFullName(), readStorage);

		repoMap.put(readPageStorage.getSignature().getFullName(), readPageStorage);

		repoMap.put(getRequest.getSignature().getFullName(), getRequest);

		repoMap.put(postRequest.getSignature().getFullName(), postRequest);

		repoMap.put(putRequest.getSignature().getFullName(), putRequest);

		repoMap.put(patchRequest.getSignature().getFullName(), patchRequest);

		repoMap.put(deleteRequest.getSignature().getFullName(), deleteRequest);

		this.filterableNames = repoMap.values().stream().map(ReactiveFunction::getSignature)
				.map(FunctionSignature::getFullName).toList();
	}

	@Override
	public Flux<String> filter(String name) {
		final String filterName = name == null ? "" : name;
		return Flux.fromStream(filterableNames.stream())
				.filter(e -> e.toLowerCase().indexOf(filterName.toLowerCase()) != -1);
	}

	@Override
	public Mono<ReactiveFunction> find(String namespace, String name) {

		return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
	}

}