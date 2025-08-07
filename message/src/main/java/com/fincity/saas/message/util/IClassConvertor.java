package com.fincity.saas.message.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.util.SpringContextAccessor;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface IClassConvertor {

    private static ObjectMapper getMapper() {
        return SpringContextAccessor.getBean(ObjectMapper.class);
    }

    private static MultiValueMap<String, String> mapToFormData(Map<String, Object> map) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        if (map == null || map.isEmpty()) {
            return formData;
        }

        map.forEach((key, value) -> {
            if (value == null) return;

            if (value.getClass().isArray()) {
                IntStream.range(0, Array.getLength(value))
                        .forEach(i -> {
                            Object item = Array.get(value, i);
                            if (item != null) {
                                formData.add(key + "[" + i + "]", item.toString());
                            }
                        });
            } else if (value instanceof Collection<?> collection) {
                int index = 0;
                for (Object item : collection) {
                    if (item != null) {
                        formData.add(key + "[" + index + "]", item.toString());
                        index++;
                    }
                }
            } else {
                formData.add(key, value.toString());
            }
        });

        return formData;
    }

    @JsonIgnore
    default Map<String, Object> toMap() {
        return getMapper().convertValue(this, new TypeReference<>() {});
    }

    @JsonIgnore
    default Mono<Map<String, Object>> toMapAsync() {
        return Mono.fromCallable(this::toMap).subscribeOn(Schedulers.boundedElastic());
    }

    @JsonIgnore
    default MultiValueMap<String, String> toFormData() {
        return mapToFormData(this.toMap());
    }

    @JsonIgnore
    default Mono<MultiValueMap<String, String>> toFormDataAsync() {
        return this.toMapAsync().map(IClassConvertor::mapToFormData);
    }
}
