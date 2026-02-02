package com.fincity.saas.entity.processor.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CollectionUtil {

    public static <K, V> Map<K, V> merge(Map<K, V> base, Map<K, V> extras) {
        Map<K, V> merged = new HashMap<>(base);
        merged.putAll(extras);
        return merged;
    }

    public static <T> List<T> intersectLists(List<?> current, List<T> mandatory) {

        if (mandatory == null || mandatory.isEmpty()) return List.of();

        if (current == null || current.isEmpty()) return mandatory;

        return mandatory.stream().filter(current::contains).toList();
    }
}
