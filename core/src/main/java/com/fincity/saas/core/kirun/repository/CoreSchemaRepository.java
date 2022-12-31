package com.fincity.saas.core.kirun.repository;

import java.util.HashMap;
import java.util.Map;

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.repository.KIRunSchemaRepository;

public class CoreSchemaRepository extends HybridRepository<Schema> {

	public static final String CORE_NAMESPACE = "Core";

	private static Map<String, Schema> map = new HashMap<>();

	public CoreSchemaRepository() {

		super(new KIRunSchemaRepository(),
		        (namespace, name) -> CORE_NAMESPACE.equals(namespace) ? map.get(name) : null);
	}
}
