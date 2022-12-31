package com.fincity.saas.core.kirun.repository;

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.function.Function;
import com.fincity.nocode.kirun.engine.repository.KIRunFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.PackageScanningFunctionRepository;

public class CoreFunctionRepository extends HybridRepository<Function> {

	public CoreFunctionRepository() {
		super(new KIRunFunctionRepository(),
		        new PackageScanningFunctionRepository("com.fincity.saas.core.kirun.function"));
	}
}
