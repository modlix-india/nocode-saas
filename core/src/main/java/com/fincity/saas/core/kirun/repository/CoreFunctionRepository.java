package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.core.functions.rest.CallRequest;
import com.fincity.saas.core.functions.security.user.GetUsersContextAuthentication;
import com.fincity.saas.core.functions.security.user.GetUsersContextUser;
import com.fincity.saas.core.functions.security.user.HasAuthority;
import com.fincity.saas.core.functions.storage.CreateStorageObject;
import com.fincity.saas.core.functions.storage.DeleteStorageObject;
import com.fincity.saas.core.functions.storage.ReadPageStorageObject;
import com.fincity.saas.core.functions.storage.ReadStorageObject;
import com.fincity.saas.core.functions.storage.UpdateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.fincity.saas.core.service.connection.rest.RestService;
import com.fincity.saas.core.service.security.user.UserContextService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

	private Map<String, ReactiveFunction> repoMap = new HashMap<>();

	private List<String> filterableNames;

    public CoreFunctionRepository(AppDataService appDataService, ObjectMapper objectMapper, RestService restService, UserContextService userContextService) {

		ReactiveFunction createStorage = new CreateStorageObject(appDataService);

		ReactiveFunction deleteStorage = new DeleteStorageObject(appDataService);

		ReactiveFunction updateStorage = new UpdateStorageObject(appDataService);

		ReactiveFunction readStorage = new ReadStorageObject(appDataService);

		ReactiveFunction readPageStorage = new ReadPageStorageObject(appDataService, objectMapper);

		ReactiveFunction getRequest = new CallRequest(restService, "GetRequest", "GET", false);
		ReactiveFunction postRequest = new CallRequest(restService, "PostRequest", "POST", true);
		ReactiveFunction putRequest = new CallRequest(restService, "PutRequest", "PUT", true);
		ReactiveFunction patchRequest = new CallRequest(restService, "PatchRequest", "PATCH", true);
		ReactiveFunction deleteRequest = new CallRequest(restService, "DeleteRequest", "DELETE", false);
		ReactiveFunction callRequest = new CallRequest(restService, "CallRequest", "", true);

        ReactiveFunction contextUser = new GetUsersContextUser(userContextService);
        ReactiveFunction userContextAuthentication = new GetUsersContextAuthentication(userContextService);
        ReactiveFunction hasAuthority = new HasAuthority(userContextService);

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

		repoMap.put(callRequest.getSignature().getFullName(), callRequest);

        repoMap.put(contextUser.getSignature().getFullName(), contextUser);

        repoMap.put(userContextAuthentication.getSignature().getFullName(), userContextAuthentication);

        repoMap.put(hasAuthority.getSignature().getFullName(), hasAuthority);

		this.filterableNames = repoMap.values().stream().map(ReactiveFunction::getSignature)
				.map(FunctionSignature::getFullName).toList();
	}

	@Override
	public Flux<String> filter(String name) {
		final String filterName = name == null ? "" : name;
		return Flux.fromStream(filterableNames.stream())
				.filter(e -> e.toLowerCase().contains(filterName.toLowerCase()));
	}

	@Override
	public Mono<ReactiveFunction> find(String namespace, String name) {

		return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
	}

}
