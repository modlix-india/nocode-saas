package com.fincity.saas.entity.processor.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

public interface IClassConvertor {

    @Component
    class GsonProvider {
        private static Gson gson;

        @Autowired
        public void setGson(Gson gson) {
            GsonProvider.gson = gson;
        }

        public static Gson getGson() {
            return gson;
        }
    }

    @JsonIgnore
    default Map<String, Object> toMap() {
        Gson gson = GsonProvider.getGson();
        return gson.fromJson(gson.toJson(this), new TypeToken<Map<String, Object>>() {}.getType());
    }

    @JsonIgnore
    default JsonElement toJson() {
        Gson gson = GsonProvider.getGson();
        return gson.toJsonTree(this, this.getClass());
    }
}
