package com.modlix.saas.commons2.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface IClassConvertor {

    private static Gson getGson() {
        return SpringContextAccessor.getBean(Gson.class);
    }

	private static ObjectMapper getObjectMapper() {
		return SpringContextAccessor.getBean(ObjectMapper.class);
	}

    private static MultiValueMap<String, String> mapToFormData(Map<String, Object> map) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        if (map == null || map.isEmpty())
            return formData;

        map.forEach((key, value) -> {
            if (value == null)
                return;

            if (value.getClass().isArray()) {
                addFormArrayData(formData, key, value);
            } else if (value instanceof Collection<?> collection) {
                addCollectionData(formData, key, collection);
            } else {
                formData.add(key, value.toString());
            }
        });

        return formData;
    }

    private static void addFormArrayData(MultiValueMap<String, String> formData, String key, Object value) {
        if (value == null)
            return;

        IntStream.range(0, Array.getLength(value)).forEach(i -> {
            addFormListKey(formData, key, i, Array.get(value, i));
        });
    }

    private static void addCollectionData(
            MultiValueMap<String, String> formData, String key, Collection<?> collection) {

        if (collection == null || collection.isEmpty())
            return;

        int index = 0;
        for (Object item : collection) {
            if (item != null) {
                addFormListKey(formData, key, index, item);
                index++;
            }
        }
    }

    private static void addFormListKey(MultiValueMap<String, String> formData, String key, int index, Object value) {
        if (value == null)
            return;
        formData.add(key + "[" + index + "]", value.toString());
    }

    @JsonIgnore
    default Map<String, Object> toMap() {
        Gson gson = getGson();
        return gson.fromJson(gson.toJson(this), new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    @JsonIgnore
    default JsonElement toJson() {
        return getGson().toJsonTree(this, this.getClass());
    }

	@JsonIgnore
	default JsonNode toJsonNode() {
		ObjectMapper mapper = getObjectMapper();
		return mapper.valueToTree(this);
	}

    @JsonIgnore
    default MultiValueMap<String, String> toFormData() {
        return mapToFormData(this.toMap());
    }
}
