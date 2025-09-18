package com.modlix.saas.commons2.util;

import static com.modlix.saas.commons2.util.CommonsUtil.*;

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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class DifferenceExtractor {

	public static Map<String, ?> extract(Map<String, ?> incoming, Map<String, ?> existing) { // NOSONAR

		// This has to be as generic as possible to handle multiple cases.

		if (existing == null || existing.isEmpty()) {
			if (incoming == null || incoming.isEmpty())
				return Map.of();
			else
				return incoming;
		}

		if (incoming == null || incoming.isEmpty()) {

			HashMap<String, Object> m = new HashMap<>();
			for (String lang : existing.keySet())
				m.put(lang, null);

			return m;
		}

		Map<String, Object> result = new HashMap<>();
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(existing.keySet());
		allKeys.addAll(incoming.keySet());

		for (String key : allKeys) {
			Object diff = extract(incoming.get(key), existing.get(key));
			if (diff != null) {
				if (diff == JsonNull.INSTANCE) {
					result.put(key, null);
				} else {
					result.put(key, diff);
				}
			}
		}

		return result.isEmpty() ? Map.of() : result;
	}

	public static Map<String, Boolean> extractMapBoolean(Map<String, Boolean> incoming,
			Map<String, Boolean> existing) {

		if (existing == null || existing.isEmpty()) {
			if (incoming == null || incoming.isEmpty())
				return Map.of();
			else
				return incoming;
		}

		if (incoming == null || incoming.isEmpty()) {

			HashMap<String, Boolean> m = new HashMap<>();
			for (String lang : existing.keySet())
				m.put(lang, null);

			return m;
		}

		Map<String, Boolean> result = new HashMap<>();
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(existing.keySet());
		allKeys.addAll(incoming.keySet());

		for (String key : allKeys) {
			if (!safeEquals(incoming.get(key), existing.get(key))) {
				result.put(key, incoming.get(key));
			}
		}

		return result.isEmpty() ? Map.of() : result;
	}

	@SuppressWarnings({ "unchecked" })
	public static Object extract(Object incoming, Object existing) { // NOSONAR

		// Splitting the below logic makes no sense.

		if (existing == null) {
			if (incoming == null)
				return null;
			else
				return incoming;
		}

		if (incoming == null)
			return JsonNull.INSTANCE;

		if (existing.equals(incoming))
			return null;

		if (existing instanceof Map && incoming instanceof Map)
			return extract((Map<String, Object>) incoming, (Map<String, Object>) existing);

		if (existing instanceof IDifferentiable exc && incoming instanceof IDifferentiable inc) // NOSONAR
			return exc.extractDifference(inc);

		if (existing instanceof FunctionDefinition efd && incoming instanceof FunctionDefinition ifd)
			return extract(ifd, efd);

		if (existing instanceof Statement est && incoming instanceof Statement ist)
			return extract(ist, est);

		if (existing instanceof StatementGroup est && incoming instanceof StatementGroup ist)
			return extract(ist, est);

		if (existing instanceof ParameterReference est && incoming instanceof ParameterReference ist)
			return extract(ist, est);

		if (existing instanceof JsonElement est && incoming instanceof JsonElement ist)
			return extract(ist, est);

		return incoming;
	}

	public static JsonElement extract(JsonElement incoming, JsonElement existing) {

		if (incoming.equals(existing))
			return null;

		if (existing == null || existing.isJsonNull())
			return incoming;

		if (existing.isJsonPrimitive() || existing.isJsonArray())
			return incoming;

		if (existing.isJsonObject() && incoming.isJsonObject())
			return extract(incoming.getAsJsonObject(), existing.getAsJsonObject());

		return incoming;
	}

	private static JsonElement extract(JsonObject incoming, JsonObject existing) {

		if (existing == null || existing.size() == 0) {
			if (incoming == null || incoming.size() == 0)
				return new JsonObject();
			else
				return incoming;
		}

		if (incoming == null || incoming.size() == 0) {

			JsonObject jo = new JsonObject();
			for (String key : existing.keySet())
				jo.add(key, null);

			return jo;
		}

		JsonObject result = new JsonObject();
		java.util.Set<String> allKeys = new java.util.HashSet<>();
		allKeys.addAll(existing.keySet());
		allKeys.addAll(incoming.keySet());

		for (String key : allKeys) {
			JsonElement diff = extract(incoming.get(key), existing.get(key));
			if (diff != null) {
				result.add(key, diff);
			}
		}

		return result;
	}

	private DifferenceExtractor() {
	}

}
