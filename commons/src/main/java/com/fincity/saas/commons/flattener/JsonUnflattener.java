package com.fincity.saas.commons.flattener;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonUnflattener {

    private static final String OBJECT_NOT_FOUND = "Json object is required but not found";

    private static final String ARRAY_NOT_FOUND = "Json array is required but not found";

    private static final String NULL_VALUE_EXPECTED = "No value required for selected field but a value was provided";

    private JsonUnflattener() {

    }

    public static JsonObject unflatten(Map<String, String> flatList,
            Map<String, Set<SchemaType>> flattenedSchemaType) {

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

    private static void placeValue(String part, JsonElement pointer, String value,
            Set<SchemaType> schemaTypes) {

        if (part.indexOf('[') == -1) {

            if (!pointer.isJsonObject())
                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, OBJECT_NOT_FOUND);

            if (!StringUtil.safeIsBlank(value)) {
                pointer.getAsJsonObject().add(part, getJSONElementBySchemaType(schemaTypes, value));
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

        if (schemaTypes.contains(SchemaType.INTEGER))
            return new JsonPrimitive(Integer.valueOf(value));

        else if (schemaTypes.contains(SchemaType.LONG))
            return new JsonPrimitive(Long.valueOf(value));

        else if (schemaTypes.contains(SchemaType.FLOAT))
            return new JsonPrimitive(Float.valueOf(value));

        else if (schemaTypes.contains(SchemaType.DOUBLE))
            return new JsonPrimitive(Double.valueOf(value));

        else if (schemaTypes.contains(SchemaType.STRING))
            return new JsonPrimitive(value);

        else if (schemaTypes.contains(SchemaType.BOOLEAN))
            return new JsonPrimitive(Boolean.valueOf(value));

        else if (schemaTypes.contains(SchemaType.NULL))
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, NULL_VALUE_EXPECTED);

        else
            return JsonNull.INSTANCE;
    }
}
