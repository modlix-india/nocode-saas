package com.modlix.saas.commons2.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.model.ParameterReference;
import com.fincity.nocode.kirun.engine.model.Position;
import com.fincity.nocode.kirun.engine.model.Statement;
import com.fincity.nocode.kirun.engine.model.StatementGroup;
import com.modlix.saas.commons2.difference.IDifferentiable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DifferenceApplicator {

	public static Map<String, ?> apply(Map<String, ?> override, Map<String, ?> base) { // NOSONAR
		// Need to be generic as the maps maybe of different type.

		if (override == null || override.isEmpty())
			return base;

		if (base == null || base.isEmpty())
			return override;

		Map<String, Object> result = new HashMap<>();

		// Add all keys from both maps
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(base.keySet());
		allKeys.addAll(override.keySet());

		for (String key : allKeys) {
			if (!override.containsKey(key) && base.get(key) != null) {
				result.put(key, base.get(key));
			} else if (!base.containsKey(key) && override.get(key) != null) {
				result.put(key, override.get(key));
			} else {
				Object applied = apply(override.get(key), base.get(key));
				if (applied != null) {
					result.put(key, applied);
				}
			}
		}

		return result.isEmpty() ? Map.of() : result;
	}

	public static Map<String, Boolean> applyMapBoolean(Map<String, Boolean> override, Map<String, Boolean> base) {
		if (override == null || override.isEmpty())
			return base;

		if (base == null || base.isEmpty())
			return override;

		Map<String, Boolean> result = new HashMap<>();

		// Add all keys from both maps
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(base.keySet());
		allKeys.addAll(override.keySet());

		for (String key : allKeys) {
			Boolean value = override.containsKey(key) ? override.get(key) : base.get(key);
			if (value != null) {
				result.put(key, value);
			}
		}

		return result.isEmpty() ? Map.of() : result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object apply(Object override, Object base) {

		if (override == null)
			return null;

		if (override instanceof Map && base instanceof Map)
			return apply((Map<String, Object>) override, (Map<String, Object>) base);

		if (override instanceof IDifferentiable inc)
			return inc.applyOverride((IDifferentiable) base);

		if (override instanceof FunctionDefinition ifd && base instanceof FunctionDefinition efd)
			return apply(ifd, efd);

		if (override instanceof Statement ist)
			return apply(ist, (Statement) base);

		if (override instanceof StatementGroup ist)
			return apply(ist, (StatementGroup) base);

		if (override instanceof ParameterReference ist && base instanceof ParameterReference est)
			return apply(ist, est);

		if (override instanceof JsonElement ist && base instanceof JsonElement est)
			return apply(ist, est);

		return override;
	}

	private static JsonElement apply(JsonElement override, JsonElement base) {

		if (override == null || override.isJsonNull())
			return base == null || base.isJsonNull() ? null : base;

		if (base == null || base.isJsonNull())
			return override.isJsonNull() ? null : override;

		if (override.isJsonObject() && base.isJsonObject())
			return apply(override.getAsJsonObject(), base.getAsJsonObject());

		return override;
	}

	private static JsonElement apply(JsonObject override, JsonObject base) {

		if (base == null || base.size() == 0) {
			if (override == null || override.size() == 0)
				return new JsonObject();
			else
				return override;
		}

		if (override == null || override.size() == 0) {
			JsonObject jo = new JsonObject();
			for (String key : base.keySet())
				jo.add(key, null);
			return jo;
		}

		JsonObject result = new JsonObject();
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(base.keySet());
		allKeys.addAll(override.keySet());

		for (String key : allKeys) {
			JsonElement applied = apply(override.get(key), base.get(key));
			if (applied != null) {
				result.add(key, applied);
			}
		}

		return result.size() == 0 ? new JsonObject() : result;
	}

	private static Object apply(ParameterReference override, ParameterReference base) {

		ParameterReference pr = override;

		pr.setKey(base.getKey());

		if (override.getExpression() == null)
			override.setExpression(base.getExpression());
		if (override.getType() == null)
			override.setType(base.getType());
		if (override.getValue() == null)
			override.setValue(base.getValue());

		return pr;
	}

	private static Object apply(StatementGroup override, StatementGroup base) {

		if (base == null)
			return override.isOverride() ? null : override;

		Object statMap = apply(override.getStatements(), base.getStatements());

		override.setStatementGroupName(base.getStatementGroupName());
		override.setPosition(apply(override.getPosition(), base.getPosition()));

		if (override.getComment() == null)
			override.setComment(base.getComment());

		if (override.getDescription() == null)
			override.setDescription(base.getDescription());

		override.setOverride(true);
		return override;
	}

	@SuppressWarnings("unchecked")
	private static Object apply(Statement override, Statement base) {

		if (base == null)
			return override.isOverride() ? null : override;

		Map<String, Boolean> depMap = applyMapBoolean(override.getDependentStatements(), base.getDependentStatements());
		Object paramMap = apply(override.getParameterMap(), base.getParameterMap());

		override.setStatementName(base.getStatementName());
		override.setDependentStatements(depMap);
		override.setParameterMap((Map<String, Map<String, ParameterReference>>) paramMap);
		override.setPosition(apply(override.getPosition(), base.getPosition()));

		if (override.getComment() == null)
			override.setComment(base.getComment());

		if (override.getDescription() == null)
			override.setDescription(base.getDescription());

		if (override.getName() == null)
			override.setName(base.getName());

		if (override.getNamespace() == null)
			override.setNamespace(base.getNamespace());

		override.setOverride(true);
		return override;
	}

	private static Position apply(Position override, Position base) {

		if (base == null)
			return override;

		if (override == null)
			return base;

		if (override.getLeft() == null)
			override.setLeft(base.getLeft());
		if (override.getTop() != null)
			override.setTop(base.getTop());

		return override;
	}

	@SuppressWarnings("unchecked")
	private static Object apply(FunctionDefinition override, FunctionDefinition base) {

		Object stepMap = apply(override.getSteps(), base.getSteps());
		Object stepGroupMap = apply(override.getStepGroups(), base.getStepGroups());

		override.setEvents(base.getEvents());
		override.setName(base.getName());
		override.setNamespace(base.getNamespace());
		override.setParameters(base.getParameters());

		override.setSteps((Map<String, Statement>) stepMap);
		override.setStepGroups((Map<String, StatementGroup>) stepGroupMap);

		return override;
	}

	private DifferenceApplicator() {
	}
}