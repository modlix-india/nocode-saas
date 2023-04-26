package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.nocode.kirun.engine.function.Function;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.saas.core.functions.CreateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;

public class CoreFunctionRepository implements Repository<Function> {

	private Map<String, Function> repoMap = new HashMap<>();

	private List<String> filterableNames;

	public CoreFunctionRepository(AppDataService appDataService) {

		Function createStorage = new CreateStorageObject(appDataService);
		
		repoMap.put(createStorage.getSignature()
		        .getFullName(), createStorage);
		
		this.filterableNames = repoMap.values()
		        .stream()
		        .map(Function::getSignature)
		        .map(FunctionSignature::getFullName)
		        .toList();
	}

	@Override
	public List<String> filter(String name) {
		return filterableNames.stream()
		        .filter(e -> e.toLowerCase()
		                .indexOf(name.toLowerCase()) != -1)
		        .toList();
	}

	@Override
	public Function find(String namespace, String name) {

		return repoMap.get(namespace + "." + name);
	}

}