package com.fincity.saas.entity.processor.util;

import java.util.Map;

public class EagerUtil {

    private EagerUtil() {}

    public static Map<String, String> getRelationMap(Class<?> clazz) {
        return (Map<String, String>) ReflectionUtil.getStaticFieldValue(clazz, "relationsMap", Map.class);
    }
}
