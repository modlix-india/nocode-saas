package com.modlix.saas.commons2.util;

import java.lang.constant.Constable;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneUtil {

    private static final Logger logger = LoggerFactory.getLogger(CloneUtil.class);

    private CloneUtil() {
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> T cloneObject(T obj) {

        if (obj == null || obj instanceof Constable)
            return obj;

        if (obj instanceof Map map)
            return (T) cloneMapObject(map);

        if (obj instanceof List lst)
            return (T) cloneMapList(lst);

        try {
            Constructor x = obj.getClass().getConstructor(obj.getClass());
            obj = (T) x.newInstance(obj);
        } catch (Exception e) {
            logger.error("Unable to create a clone instance for : {}", obj.getClass());
        }

        return obj;
    }

    public static <V> List<V> cloneMapList(List<V> lst) {

        if (lst == null)
            return List.of();

        return lst.stream().map(CloneUtil::cloneObject).filter(Objects::nonNull).toList();
    }

    public static <K, V> Map<K, V> cloneMapObject(Map<K, V> map) {

        if (map == null)
            return Map.of();

        return map.entrySet().stream()
                .filter(e -> Objects.nonNull(e.getValue()))
                .map(e -> {
                    V k = cloneObject(e.getValue());

                    if (k == null)
                        return null;

                    return Tuples.of(e.getKey(), k);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuples.Tuple2::getT1, Tuples.Tuple2::getT2));
    }

    public static Map<String, Map<String, String>> cloneMapStringMap(Map<String, Map<String, String>> map) {

        if (map == null)
            return Map.of();

        return map.entrySet().stream()
                .filter(e -> Objects.nonNull(e.getValue()))
                .map(e -> Tuples.of(
                        e.getKey(),
                        e.getValue().entrySet().stream()
                                .filter(f -> Objects.nonNull(f.getValue()))
                                .map(f -> Tuples.of(f.getKey(), f.getValue()))
                                .collect(Collectors.toMap(Tuples.Tuple2::getT1, Tuples.Tuple2::getT2))))
                .collect(Collectors.toMap(Tuples.Tuple2::getT1, Tuples.Tuple2::getT2));
    }
}
