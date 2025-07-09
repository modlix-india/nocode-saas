package com.fincity.saas.entity.processor.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface IClassConvertor {

    private static Gson getGson() {
        return SpringContextAccessor.getBean(Gson.class);
    }

    @JsonIgnore
    default Map<String, Object> toMap() {
        Gson gson = getGson();
        return gson.fromJson(gson.toJson(this), new TypeToken<Map<String, Object>>() {}.getType());
    }

    @JsonIgnore
    default JsonElement toJson() {
        return getGson().toJsonTree(this, this.getClass());
    }

    @JsonIgnore
    default Mono<Map<String, Object>> toMapAsync() {
        return Mono.fromCallable(this::toMap).subscribeOn(Schedulers.boundedElastic());
    }

    @JsonIgnore
    default Mono<JsonElement> toJsonAsync() {
        return Mono.fromCallable(this::toJson).subscribeOn(Schedulers.boundedElastic());
    }
}
