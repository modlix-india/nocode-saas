package com.fincity.saas.entity.processor.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jooq.Table;

public class EagerUtil {

    private EagerUtil() {}

    public static Map<String, Table<?>> getRelationMap(Class<?> clazz) {
        return (Map<String, Table<?>>) ReflectionUtil.getStaticFieldValue(clazz, "relationsMap", Map.class);
    }

    public static String toJooqField(String fieldName) {

        if (fieldName == null || fieldName.isEmpty())
            throw new IllegalArgumentException("Field name cannot be null or empty.");

        StringBuilder stringBuilder = new StringBuilder();

        for (char c : fieldName.toCharArray()) {
            if (Character.isUpperCase(c)) stringBuilder.append("_");
            stringBuilder.append(Character.toUpperCase(c));
        }
        return stringBuilder.toString();
    }

    public static String fromJooqField(String jooqFieldName) {
        if (jooqFieldName == null || jooqFieldName.isEmpty())
            throw new IllegalArgumentException("Field name cannot be null or empty.");

        StringBuilder stringBuilder = new StringBuilder();
        boolean toUpperCaseNext = false;

        for (char c : jooqFieldName.toCharArray()) {
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
    }

    public static Map<String, Object> convertMapKeysToCamelCase(Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();

            String[] parts = key.split("\\.");

            StringBuilder transformedKey = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String camel = EagerUtil.fromJooqField(parts[i]);
                if (i > 0) transformedKey.append(".");
                transformedKey.append(camel);
            }

            output.put(transformedKey.toString(), entry.getValue());
        }

        return output;
    }
}
