package com.fincity.saas.ui.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fincity.saas.ui.model.ComponentDefinition;

public class DifferenceApplicator {

	public static Map<String, ?> jsonMap(Map<String, ?> override, Map<String, ?> base) { // NOSONAR
		// Need to be generic as the maps maybe of different type.

		if (override == null)
			return base;

		if (base == null)
			return override;

		Map<String, Object> newOne = new HashMap<>();

		baseChanges(override, base, newOne);

		overrideChanges(override, base, newOne);

		return newOne;
	}

	private static void overrideChanges(Map<String, ?> override, Map<String, ?> base, Map<String, Object> newOne) {
		Set<String> baseKeys = base.keySet();

		for (Entry<String, ?> e : override.entrySet()) {

			if (baseKeys.contains(e.getKey()))
				continue;

			if (e.getValue() instanceof ComponentDefinition cd) {

				if (!cd.isOverride())
					newOne.put(e.getKey(), cd);
			} else {

				newOne.put(e.getKey(), e.getValue());
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void baseChanges(Map<String, ?> override, Map<String, ?> base, Map<String, Object> newOne) {
		for (Entry<String, ?> e : base.entrySet()) {

			Object oObject = override.get(e.getKey());

			if (oObject == null) {

				newOne.put(e.getKey(), e.getValue());
			} else if (oObject instanceof ComponentDefinition oCDefinition
			        && e.getValue() instanceof ComponentDefinition bCDefinition) {

				newOne.put(e.getKey(), jsonComponentDefinition(oCDefinition, bCDefinition));
			} else if (oObject instanceof Map oMap && e.getValue() instanceof Map bMap) {

				newOne.put(e.getKey(), jsonMap(oMap, bMap));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static ComponentDefinition jsonComponentDefinition(ComponentDefinition oCDefinition,
	        ComponentDefinition bCDefinition) {

		if (oCDefinition == null)
			return bCDefinition;

		if (bCDefinition == null)
			return oCDefinition;

		ComponentDefinition nCDefinition = new ComponentDefinition();

		nCDefinition.setKey(oCDefinition.getKey() == null ? nCDefinition.getKey() : oCDefinition.getKey());
		nCDefinition.setName(oCDefinition.getName() == null ? nCDefinition.getName() : oCDefinition.getName());
		nCDefinition.setOverride(true);
		nCDefinition.setType(oCDefinition.getType() == null ? nCDefinition.getType() : oCDefinition.getType());
		nCDefinition.setProperties(
		        (Map<String, Object>) jsonMap(oCDefinition.getProperties(), bCDefinition.getProperties()));
		nCDefinition
		        .setChildren((Map<String, Boolean>) jsonMap(oCDefinition.getChildren(), bCDefinition.getChildren()));

		return nCDefinition;
	}

	private DifferenceApplicator() {
	}
}