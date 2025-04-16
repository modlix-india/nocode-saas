package com.fincity.saas.commons.flattener;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ParseException;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.util.primitive.PrimitiveUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.util.function.Tuple2;

public class JsonUnflattener {

	private static final String OBJECT_NOT_FOUND = "Json object is required but not found";

	private static final String ARRAY_NOT_FOUND = "Json array is required but not found";

	private static final Logger logger = LoggerFactory.getLogger(JsonUnflattener.class);

	private JsonUnflattener() {
	}

	public static JsonObject unflatten(Map<String, String> flatList, Map<String, Set<SchemaType>> flattenedSchemaType) {

		JsonObject job = new JsonObject();

		for (Entry<String, String> entry : flatList.entrySet()) {

			String path = entry.getKey();
			String[] parts = path.split("\\.");

			JsonElement pointer = job;

			int i = 0;
			for (; i < parts.length - 1; i++) {

				String part = parts[i];

				if (part.indexOf('[') == -1)
					pointer = processObjectOperator(pointer, part);
				else
					pointer = processArrayOperator(pointer, part);
			}

			placeValue(parts[i], pointer, entry.getValue(), flattenedSchemaType.get(entry.getKey()));
		}

		return job;
	}

	private static JsonElement processObjectOperator(JsonElement pointer, String part) {

		if (!pointer.isJsonObject())
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, OBJECT_NOT_FOUND);

		JsonObject mini = pointer.getAsJsonObject();

		if (!mini.has(part))
			mini.add(part, new JsonObject());

		pointer = mini.get(part);

		return pointer;
	}

	private static JsonElement processArrayOperator(JsonElement pointer, String part) {

		String[] subParts = part.split("\\[");

		int j = 0;
		JsonObject mini = pointer.getAsJsonObject();

		if (!mini.has(subParts[j]))
			mini.add(subParts[j], new JsonArray());

		pointer = mini.get(subParts[j]);

		for (j = 1; j < subParts.length - 1; j++) {

			if (!pointer.isJsonArray())
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ARRAY_NOT_FOUND);

			JsonArray miniArray = pointer.getAsJsonArray();
			int index = Integer.parseInt(subParts[j].substring(0, subParts[j].length() - 1));
			if (index >= miniArray.size()) {

				int size = miniArray.size();
				for (int k = 0; k < (index - size); k++)
					miniArray.add(new JsonArray());
			}

			pointer = miniArray.get(index);
		}

		int index = Integer.parseInt(subParts[j].substring(0, subParts[j].length() - 1));
		JsonArray miniArray = pointer.getAsJsonArray();
		if (index >= miniArray.size()) {

			int size = miniArray.size();
			for (int k = 0; k <= (index - size); k++)
				miniArray.add(new JsonObject());
		}

		pointer = miniArray.get(index);

		return pointer;
	}

	private static void placeValue(String part, JsonElement pointer, String value, Set<SchemaType> schemaTypes) {

		if (part.indexOf('[') == -1) {

			if (!pointer.isJsonObject())
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, OBJECT_NOT_FOUND);

			if (!StringUtil.safeIsBlank(value)) {
				pointer.getAsJsonObject()
				        .add(part, getJSONElementBySchemaType(schemaTypes, value));
			}

		} else {
			processArrayOperatorForPlaceValue(part, pointer, value, schemaTypes);
		}
	}

	private static void processArrayOperatorForPlaceValue(String part, JsonElement pointer, String value,
	        Set<SchemaType> schemaTypes) {

		String[] subParts = part.split("\\[");

		int j = 0;
		JsonObject mini = pointer.getAsJsonObject();

		if (!mini.has(subParts[j]))
			mini.add(subParts[j], new JsonObject());

		pointer = mini.get(subParts[j]);

		for (j = 1; j < subParts.length - 1; j++) {

			if (!pointer.isJsonArray())
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, ARRAY_NOT_FOUND);

			JsonArray miniArray = pointer.getAsJsonArray();
			int index = Integer.parseInt(subParts[j].substring(0, subParts[j].length() - 1));
			if (index >= miniArray.size()) {

				int size = miniArray.size();
				for (int k = 0; k <= (index - size); k++)
					miniArray.add(new JsonArray());
			}

			pointer = miniArray.get(index);
		}

		int index = Integer.parseInt(subParts[j].substring(0, subParts[j].length() - 1));
		JsonArray miniArray = pointer.getAsJsonArray();
		if (index >= miniArray.size()) {

			int size = miniArray.size();
			for (int k = 0; k < (index - size); k++)
				miniArray.add(JsonNull.INSTANCE);
		}

		if (!StringUtil.safeIsBlank(value)) {
			miniArray.set(index, getJSONElementBySchemaType(schemaTypes, value));
		}
	}

	private static JsonElement getJSONElementBySchemaType(Set<SchemaType> schemaTypes, String value) {

		if (StringUtil.safeIsBlank(value) || "null".equalsIgnoreCase(value))
			return JsonNull.INSTANCE;

		if (schemaTypes.contains(SchemaType.STRING))
			return new JsonPrimitive(value);

		if (schemaTypes.contains(SchemaType.BOOLEAN)) {

			try {
				Boolean b = BooleanUtil.parse(value);
				return new JsonPrimitive(b);
			} catch (ParseException pe) {

				logger.debug("Ignoring the exceptions while generating a value for a boolean value.", pe);
			}

		}

		Tuple2<SchemaType, Number> typeTup = PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value));

		return new JsonPrimitive(typeTup.getT2());
	}
}
