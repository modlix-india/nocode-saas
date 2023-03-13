package com.fincity.saas.commons.flattener;

import java.util.Map;
import java.util.Map.Entry;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonUnflattener {

    private static final String OBJECT_NOT_FOUND = "Json object is required but not found";

    private static final String ARRAY_NOT_FOUND = "Json array is required but not found";

    private JsonUnflattener() {

    }

    public static JsonObject unflatten(Map<String, Object> flatList) {

        JsonObject job = new JsonObject();

        for (Entry<String, Object> entry : flatList.entrySet()) {

            String path = entry.getKey();
            String[] parts = path.split("\\.");

            JsonElement pointer = job;

            int i = 0;
            for (; i < parts.length - 1; i++) {

                String part = parts[i];

                if (part.indexOf('[') == -1) {

                    if (!pointer.isJsonObject())
                        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, OBJECT_NOT_FOUND);

                    JsonObject mini = pointer.getAsJsonObject();

                    if (!mini.has(part))
                        mini.add(part, new JsonObject());

                    pointer = mini.get(part);
                } else {

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
                }
            }

            placeValue(parts[i], pointer, entry.getValue());
        }

        return job;
    }

    private static void placeValue(String part, JsonElement pointer, Object value) {

        if (part.indexOf('[') == -1) {

            if (!pointer.isJsonObject())
                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, OBJECT_NOT_FOUND);

            if (value instanceof Integer integer)
                pointer.getAsJsonObject().addProperty(part, integer);

            else if (value instanceof Long lng)
                pointer.getAsJsonObject().addProperty(part, lng);

            else if (value instanceof Double dble)
                pointer.getAsJsonObject().addProperty(part, dble);

            else if (value instanceof Boolean bool)
                pointer.getAsJsonObject().addProperty(part, bool);
            else
                pointer.getAsJsonObject().addProperty(part, value.toString());

        } else {

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

            if (value instanceof Integer integer)
                miniArray.set(index, new JsonPrimitive(integer));

            else if (value instanceof Long lng)

                miniArray.set(index, new JsonPrimitive(lng));

            else if (value instanceof Double dble)
                miniArray.set(index, new JsonPrimitive(dble));

            else if (value instanceof Boolean bool)
                miniArray.set(index, new JsonPrimitive(bool));

            else
                miniArray.set(index, new JsonPrimitive(value.toString()));

        }
    }
}
