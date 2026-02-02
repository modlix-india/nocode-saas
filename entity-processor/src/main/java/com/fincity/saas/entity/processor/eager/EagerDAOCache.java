package com.fincity.saas.entity.processor.eager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jooq.Field;
import org.jooq.Table;
import reactor.util.function.Tuple2;

public class EagerDAOCache {

    private static final Map<Table<?>, List<Field<?>>> tableFieldsCache = new ConcurrentHashMap<>();

    private static final Map<Tuple2<Table<?>, String>, Map<String, Field<?>>> tableAliasedFieldsCache =
            new ConcurrentHashMap<>();

    private EagerDAOCache() {}

    public static List<Field<?>> getTableFieldsCache(
            Table<?> table, Function<Table<?>, List<Field<?>>> computeFunction) {
        return tableFieldsCache.computeIfAbsent(table, computeFunction);
    }

    public static Map<String, Field<?>> getTableAliasedFieldsCache(
            Tuple2<Table<?>, String> tableTuple,
            Function<Tuple2<Table<?>, String>, Map<String, Field<?>>> computeFunction) {
        return tableAliasedFieldsCache.computeIfAbsent(tableTuple, computeFunction);
    }
}
