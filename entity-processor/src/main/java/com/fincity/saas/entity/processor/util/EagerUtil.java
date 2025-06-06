package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.util.ConditionUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Table;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class EagerUtil {

    private static final String RELATIONS_MAP = "relationsMap";
    private static final String EAGER_FIELD = "eagerField";

    private static final Map<String, String> fieldNameCache = new ConcurrentHashMap<>();
    private static final Map<String, String> jooqFieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Tuple2<Table<?>, String>>> relationMapCache =
            new ConcurrentHashMap<>();

    private EagerUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Tuple2<Table<?>, String>> getRelationMap(Class<?> clazz) {
        return relationMapCache.computeIfAbsent(clazz, key -> {
            Map<String, Table<?>> relations = ReflectionUtil.getStaticFieldValueNoError(key, RELATIONS_MAP, Map.class);
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

    public static List<String> getEagerParams(Map<String, List<String>> multiValueMap) {
        return multiValueMap.containsKey(EAGER_FIELD) ? multiValueMap.get(EAGER_FIELD) : List.of();
    }

    public static Tuple2<AbstractCondition, List<String>> getEagerConditions(Map<String, List<String>> multiValueMap) {

        if (multiValueMap.isEmpty()) return Tuples.of(new ComplexCondition().setConditions(List.of()), List.of());

        MultiValueMap<String, String> copyMap = new LinkedMultiValueMap<>(multiValueMap);

        List<String> eagerFields = getEagerParams(copyMap);
        copyMap.remove(EAGER_FIELD);

        AbstractCondition condition = ConditionUtil.parameterMapToMap(copyMap);

        if (condition == null) condition = new ComplexCondition().setConditions(List.of());

        return Tuples.of(condition, eagerFields);
    }
}
