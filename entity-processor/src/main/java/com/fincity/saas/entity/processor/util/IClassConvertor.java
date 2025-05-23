package com.fincity.saas.entity.processor.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.Map;

public interface IClassConvertor {

    Gson GSON = new Gson();

    @JsonIgnore
    default Map<String, Object> toMap() {
        return GSON.fromJson(GSON.toJson(this), new TypeToken<Map<String, Object>>() {}.getType());
    }

    @JsonIgnore
    default JsonElement toJson() {
        return GSON.toJsonTree(this, this.getClass());
    }
}
