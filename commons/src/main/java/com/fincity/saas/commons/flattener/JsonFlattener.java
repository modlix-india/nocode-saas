package com.fincity.saas.commons.flattener;

import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.util.primitive.PrimitiveUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.util.function.Tuple2;

public class JsonFlattener {

    private static final String PRIMITIVE_NOT_FOUND = "Primitive data is required but not found";

    private static final Logger logger = LoggerFactory.getLogger(JsonFlattener.class);

    public static Map<String, String> convertToFlat(
            JsonElement job, String prefix, Map<String, Set<SchemaType>> flattenedSchemaType) {

        Map<String, String> finalMap = new HashMap<>();

        return flatten(job, prefix, finalMap, flattenedSchemaType);
    }

    private static Map<String, String> flatten(
            JsonElement job,
            String prefix,
            Map<String, String> finalMap,
            Map<String, Set<SchemaType>> flattenedSchemaType)
            throws GenericException {

        if (job.isJsonObject()) {
            processAsJsonObject(job, prefix, finalMap, flattenedSchemaType);
        } else if (job.isJsonArray()) {
            processAsJsonArray(job, prefix, finalMap, flattenedSchemaType);
        } else if (job.isJsonNull()) {
            finalMap.put(prefix, "null");
        } else if (job.isJsonPrimitive()) {
            finalMap.put(prefix, verifySchemaType(flattenedSchemaType.get(prefix), job));
        }

        return finalMap;
    }

    private static void processAsJsonObject(
            JsonElement j1, String prefix, Map<String, String> finalMap, Map<String, Set<SchemaType>> schemaTypes) {

        JsonObject receivedObject = j1.getAsJsonObject();

        Set<String> keys = receivedObject.keySet();
        for (String key : keys) {
            flatten(receivedObject.get(key), prefix + "." + key, finalMap, schemaTypes);
        }
    }

    private static void processAsJsonArray(
            JsonElement j2, String prefix, Map<String, String> finalMap, Map<String, Set<SchemaType>> schemaTypes) {

        JsonArray receivedArray = j2.getAsJsonArray();

        for (int i = 0; i < receivedArray.size(); i++) {
            flatten(receivedArray.get(i), prefix + "[" + i + "]", finalMap, schemaTypes); // index of the array should
            // be changed ??
        }
    }

    private static String verifySchemaType(Set<SchemaType> schemaTypes, JsonElement value) {

        try {

            Tuple2<SchemaType, Object> obtainedSchema = PrimitiveUtil.findPrimitive(value);
            if (schemaTypes.contains(obtainedSchema.getT1())) return value.getAsString();

        } catch (Exception e) {

            logger.debug("Ignoring the exceptions while verifying.", e);
        }

        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, PRIMITIVE_NOT_FOUND);
    }

    private JsonFlattener() {}
}
