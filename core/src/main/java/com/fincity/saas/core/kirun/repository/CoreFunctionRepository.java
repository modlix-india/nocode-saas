package com.fincity.saas.core.kirun.repository;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.nocode.kirun.engine.function.Function;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.repository.KIRunFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.PackageScanningFunctionRepository;
import com.fincity.saas.core.functions.CreateStorageObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;

public class CoreFunctionRepository extends HybridRepository<Function> {

	public CoreFunctionRepository() {
		super(new KIRunFunctionRepository(),
		        new PackageScanningFunctionRepository("com.fincity.saas.core.kirun.function"));

	}

	public CoreFunctionRepository(AppDataService appDataService) {
		super(new KIRunFunctionRepository(),
		        new PackageScanningFunctionRepository("com.fincity.saas.core.kirun.function"),
		        new Repository<Function>() {

			        private static final Function CREATE_STORAGE = new CreateStorageObject(appDataService);
			        private static final Map<String, Function> REPO_MAP = Map.of(CREATE_STORAGE.getSignature()
			                .getFullName(), CREATE_STORAGE);

			        private static final List<String> FILTERABLE_NAMES = REPO_MAP.values()
			                .stream()
			                .map(Function::getSignature)
			                .map(FunctionSignature::getFullName)
			                .toList();

			        @Override
			        public List<String> filter(String name) {
				        return FILTERABLE_NAMES.stream()
				                .filter(e -> e.toLowerCase()
				                        .indexOf(name.toLowerCase()) != -1)
				                .toList();
			        }

			        @Override
			        public Function find(String namespace, String name) {

				        return REPO_MAP.get(namespace + "." + name);
			        }

		        });
	}

}
