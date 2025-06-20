package com.fincity.saas.entity.processor.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Field;
import org.jooq.Table;
import reactor.util.function.Tuple2;

public class EagerDAOCache {

    private static final Map<Table<?>, List<Field<?>>> tableFieldsCache = new ConcurrentHashMap<>();

    private static final Map<Tuple2<Table<?>, String>, Map<String, Field<?>>> tableAliasedFieldsCache =
            new ConcurrentHashMap<>();

    private EagerDAOCache() {
        // Private constructor to prevent instantiation
    }

    public static List<Field<?>> getTableFieldsCache(
            Table<?> table, java.util.function.Function<Table<?>, List<Field<?>>> computeFunction) {
        return tableFieldsCache.computeIfAbsent(table, computeFunction);
    }

    public static Map<String, Field<?>> getTableAliasedFieldsCache(
            Tuple2<Table<?>, String> tableTuple,
            java.util.function.Function<Tuple2<Table<?>, String>, Map<String, Field<?>>> computeFunction) {
        return tableAliasedFieldsCache.computeIfAbsent(tableTuple, computeFunction);
    }
}
