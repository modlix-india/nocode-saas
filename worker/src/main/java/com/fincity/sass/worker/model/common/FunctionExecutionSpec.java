package com.fincity.sass.worker.model.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FunctionExecutionSpec {

    private static final Gson GSON = new Gson();

    private String namespace;
    private String name;
    private Map<String, JsonElement> params = new HashMap<>();

    public static FunctionExecutionSpec fromJobData(Map<String, Object> jobData) {
        if (jobData == null || jobData.isEmpty()) return null;
        String namespace = getString(jobData, "functionNamespace");
        String name = getString(jobData, "functionName");
        if (namespace == null && name == null) return null;
        Map<String, JsonElement> params = parseParams(jobData.get("functionParams"));
        return new FunctionExecutionSpec(namespace, name, params);
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static Map<String, JsonElement> parseParams(Object paramsObj) {
        if (paramsObj == null) return Collections.emptyMap();
        if (paramsObj instanceof String s) {
            if (s.isBlank()) return Collections.emptyMap();
            try {
                return GSON.fromJson(s, new TypeToken<Map<String, JsonElement>>() {}.getType());
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }
        if (paramsObj instanceof Map) {
            return GSON.fromJson(GSON.toJson(paramsObj), new TypeToken<Map<String, JsonElement>>() {}.getType());
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> toJobData() {
        Map<String, Object> map = new HashMap<>();
        if (namespace != null) map.put("functionNamespace", namespace);
        if (name != null) map.put("functionName", name);
        if (params != null && !params.isEmpty()) {
            map.put("functionParams", GSON.toJson(params));
        }
        return map;
    }

    public void mergeInto(Map<String, Object> jobData) {
        if (jobData == null) return;
        if (namespace != null) jobData.put("functionNamespace", namespace);
        if (name != null) jobData.put("functionName", name);
        if (params != null && !params.isEmpty()) {
            jobData.put("functionParams", GSON.toJson(params));
        }
    }

    public boolean hasFunctionSpec() {
        return name != null && namespace != null;
    }
}
