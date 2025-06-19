package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.entity.processor.relations.IRelationMap;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Table;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

public class EagerUtil {

    private static final String EAGER = "eager";
    private static final String EAGER_FIELD = "eagerField";
    private static final String FIELD = "field";

    private static final Map<String, String> fieldNameCache = new ConcurrentHashMap<>();
    private static final Map<String, String> jooqFieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Tuple2<Table<?>, String>>> relationMapCache =
            new ConcurrentHashMap<>();

    private static final Map<Class<?>, SetValuedMap<Class<? extends RelationResolver>, String>>
            relationResolverMapCache = new ConcurrentHashMap<>();

    private EagerUtil() {}

    public static <T extends IRelationMap> Map<String, Tuple2<Table<?>, String>> getRelationMap(Class<T> clazz) {
        return relationMapCache.computeIfAbsent(clazz, key -> {
            Map<String, Table<?>> relations = ReflectionUtil.getInstance(clazz).getRelationsMap();
            Map<String, Tuple2<Table<?>, String>> result = new LinkedHashMap<>();

            if (relations != null)
                relations.forEach(
                        (relationKey, table) -> result.put(relationKey, Tuples.of(table, toJooqField(relationKey))));

            return result;
        });
    }

    public static <T extends IRelationMap>
            SetValuedMap<Class<? extends RelationResolver>, String> getRelationResolverMap(Class<T> clazz) {
        return relationResolverMapCache.computeIfAbsent(
                clazz, key -> ReflectionUtil.getInstance(clazz).getRelationsResolverMap());
    }

    public static String toJooqField(String fieldName) {
        return jooqFieldCache.computeIfAbsent(
                fieldName, key -> key.replaceAll("[A-Z0-9]", "_$0").toUpperCase());
    }

    public static String fromJooqField(String jooqFieldName) {
        if (jooqFieldName == null || jooqFieldName.isEmpty())
            throw new IllegalArgumentException("Field name cannot be null or empty.");

        return fieldNameCache.computeIfAbsent(jooqFieldName, key -> {
            StringBuilder sb = new StringBuilder(key.length());
            boolean upperNext = false;

            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c == '_') {
                    upperNext = true;
                } else {
                    sb.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                    upperNext = false;
                }
            }

            return sb.toString();
        });
    }

    public static List<String> getEagerParams(Map<String, List<String>> multiValueMap) {
        return multiValueMap.containsKey(EAGER_FIELD) ? multiValueMap.get(EAGER_FIELD) : List.of();
    }

    public static Boolean getIsEagerParams(Map<String, List<String>> multiValueMap) {
        return multiValueMap.containsKey(EAGER)
                ? BooleanUtil.parse(multiValueMap.get(EAGER).getFirst())
                : Boolean.FALSE;
    }

    public static List<String> getFieldParams(Map<String, List<String>> multiValueMap) {
        return multiValueMap.containsKey(FIELD) ? multiValueMap.get(FIELD) : List.of();
    }

    public static Tuple4<AbstractCondition, List<String>, Boolean, List<String>> getEagerConditions(
            Map<String, List<String>> multiValueMap) {

        if (multiValueMap.isEmpty())
            return Tuples.of(new ComplexCondition().setConditions(List.of()), List.of(), Boolean.TRUE, List.of());

        MultiValueMap<String, String> copyMap = new LinkedMultiValueMap<>(multiValueMap);

        List<String> tableFields = getFieldParams(copyMap);
        copyMap.remove(FIELD);

        Boolean eager = getIsEagerParams(copyMap);
        copyMap.remove(EAGER);

        List<String> eagerFields = Boolean.TRUE.equals(eager) ? getEagerParams(copyMap) : List.of();
        copyMap.remove(EAGER_FIELD);

        AbstractCondition condition = ConditionUtil.parameterMapToMap(copyMap);

        if (condition == null) condition = new ComplexCondition().setConditions(List.of());

        return Tuples.of(condition, tableFields, eager, eagerFields);
    }
}
