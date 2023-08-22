package com.fincity.saas.commons.mongo.util;

import java.util.HashMap;
import java.util.Map;

public class MergeMapUtil {

	@SuppressWarnings({ "unchecked" })
	public static <T> Map<String, T> merge(Map<String, T> map1, Map<String, T> map2) { // NOSONAR
		// For just 2 point it is not required to break the logic.

		if (map1 == null || map1.isEmpty())
			return map2 == null || map2.isEmpty() ? Map.of() : map2;

		if (map2 == null || map2.isEmpty())
			return map1.isEmpty() ? Map.of() : map1;

		Map<String, T> outMap = new HashMap<>();

		for (var entry : map1.entrySet()) {

			if (!map2.containsKey(entry.getKey())) {
				outMap.put(entry.getKey(), entry.getValue());
				continue;
			}

			T v2 = map2.get(entry.getKey());

			if (v2 instanceof Map vmap2) { // NOSONAR
				outMap.put(entry.getKey(), (T) merge((Map<String, Object>) entry.getValue(), vmap2));
			}
		}

		for (var entry : map2.entrySet()) {

			if (outMap.containsKey(entry.getKey()))
				continue;

			outMap.put(entry.getKey(), entry.getValue());
		}

		return outMap;
	}

	private MergeMapUtil() {
	}
}
