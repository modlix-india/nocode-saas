package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.core.functions.rest.CallRequest;
import com.fincity.saas.core.functions.security.GetClient;
import com.fincity.saas.core.functions.security.IsBeingManagedByCode;
import com.fincity.saas.core.functions.security.IsBeingManagedById;
import com.fincity.saas.core.functions.securitycontext.GetAuthentication;
import com.fincity.saas.core.functions.securitycontext.GetUser;
import com.fincity.saas.core.functions.securitycontext.HasAuthority;
import com.fincity.saas.core.functions.storage.CreateStorageObject;
import com.fincity.saas.core.functions.storage.DeleteStorageObject;
import com.fincity.saas.core.functions.storage.ReadPageStorageObject;
import com.fincity.saas.core.functions.storage.ReadStorageObject;
import com.fincity.saas.core.functions.storage.UpdateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.fincity.saas.core.service.connection.rest.RestService;
import com.fincity.saas.core.service.security.ContextService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

	private final Map<String, ReactiveFunction> repoMap = new HashMap<>();

	private final List<String> filterableNames;

	public CoreFunctionRepository(AppDataService appDataService, ObjectMapper objectMapper, RestService restService,
			ContextService userContextService, IFeignSecurityService securityService) {

		this.makeStorageFunctions(appDataService, objectMapper);
		this.makeRESTFunctions(restService);
		this.makeSecurityContextFunctions(userContextService);
		this.makeSecurityFunctions(securityService);

		this.filterableNames = repoMap.values().stream().map(ReactiveFunction::getSignature)
				.map(FunctionSignature::getFullName).toList();
	}

	private void makeSecurityFunctions(IFeignSecurityService securityService) {

		ReactiveFunction isBeingManagedByCode = new IsBeingManagedByCode(securityService);
		ReactiveFunction isBeingManagedById = new IsBeingManagedById(securityService);
		ReactiveFunction getClient = new GetClient(securityService);

		repoMap.put(isBeingManagedByCode.getSignature().getFullName(), isBeingManagedByCode);
		repoMap.put(isBeingManagedById.getSignature().getFullName(), isBeingManagedById);
		repoMap.put(getClient.getSignature().getFullName(), getClient);
	}

	private void makeSecurityContextFunctions(ContextService userContextService) {

		ReactiveFunction hasAuthority = new HasAuthority(userContextService);
		ReactiveFunction getAuthentication = new GetAuthentication(userContextService);
		ReactiveFunction getUser = new GetUser(userContextService);

		repoMap.put(hasAuthority.getSignature().getFullName(), hasAuthority);
		repoMap.put(getAuthentication.getSignature().getFullName(), getAuthentication);
		repoMap.put(getUser.getSignature().getFullName(), getUser);
	}

	private void makeStorageFunctions(AppDataService appDataService, ObjectMapper objectMapper) {

		ReactiveFunction createStorage = new CreateStorageObject(appDataService);
		ReactiveFunction deleteStorage = new DeleteStorageObject(appDataService);
		ReactiveFunction updateStorage = new UpdateStorageObject(appDataService);
		ReactiveFunction readStorage = new ReadStorageObject(appDataService);
		ReactiveFunction readPageStorage = new ReadPageStorageObject(appDataService, objectMapper);

		repoMap.put(createStorage.getSignature().getFullName(), createStorage);
		repoMap.put(deleteStorage.getSignature().getFullName(), deleteStorage);
		repoMap.put(updateStorage.getSignature().getFullName(), updateStorage);
		repoMap.put(readStorage.getSignature().getFullName(), readStorage);
		repoMap.put(readPageStorage.getSignature().getFullName(), readPageStorage);
	}

	private void makeRESTFunctions(RestService restService) {

		ReactiveFunction getRequest = new CallRequest(restService, "GetRequest", "GET", false);
		ReactiveFunction postRequest = new CallRequest(restService, "PostRequest", "POST", true);
		ReactiveFunction putRequest = new CallRequest(restService, "PutRequest", "PUT", true);
		ReactiveFunction patchRequest = new CallRequest(restService, "PatchRequest", "PATCH", true);
		ReactiveFunction deleteRequest = new CallRequest(restService, "DeleteRequest", "DELETE", false);
		ReactiveFunction callRequest = new CallRequest(restService, "CallRequest", "", true);

		repoMap.put(getRequest.getSignature().getFullName(), getRequest);
		repoMap.put(postRequest.getSignature().getFullName(), postRequest);
		repoMap.put(putRequest.getSignature().getFullName(), putRequest);
		repoMap.put(patchRequest.getSignature().getFullName(), patchRequest);
		repoMap.put(deleteRequest.getSignature().getFullName(), deleteRequest);
		repoMap.put(callRequest.getSignature().getFullName(), callRequest);

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
