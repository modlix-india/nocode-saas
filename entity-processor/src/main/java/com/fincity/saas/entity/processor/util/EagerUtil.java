package com.fincity.saas.entity.processor.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Table;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class EagerUtil {

    private static final Map<String, String> fieldNameCache = new ConcurrentHashMap<>();
    private static final Map<String, String> jooqFieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Tuple2<Table<?>, String>>> relationMapCache =
            new ConcurrentHashMap<>();

    private EagerUtil() {}

    public static Map<String, Tuple2<Table<?>, String>> getRelationMap(Class<?> clazz) {
        return relationMapCache.computeIfAbsent(clazz, key -> {
            Map<String, Table<?>> relations =
                    (Map<String, Table<?>>) ReflectionUtil.getStaticFieldValue(key, "relationsMap", Map.class);
            Map<String, Tuple2<Table<?>, String>> result = new LinkedHashMap<>();

            if (relations != null)
                relations.forEach(
                        (relationKey, table) -> result.put(relationKey, Tuples.of(table, toJooqField(relationKey))));

            return result;
        });
    }

    private static String toJooqField(String fieldName) {
        return jooqFieldCache.computeIfAbsent(
                fieldName, key -> key.replaceAll("([A-Z])", "_$1").toUpperCase());
    }

    public static String fromJooqField(String jooqFieldName) {
        if (jooqFieldName == null || jooqFieldName.isEmpty())
            throw new IllegalArgumentException("Field name cannot be null or empty.");

        return fieldNameCache.computeIfAbsent(jooqFieldName, key -> {
            StringBuilder stringBuilder = new StringBuilder();
            boolean toUpperCaseNext = false;

            for (char c : key.toCharArray()) {
                if (c == '_') {
                    toUpperCaseNext = true;
                } else if (toUpperCaseNext) {
                    stringBuilder.append(Character.toUpperCase(c));
                    toUpperCaseNext = false;
                } else {
                    stringBuilder.append(Character.toLowerCase(c));
                }
            }

            return stringBuilder.toString();
        });
    }
}
