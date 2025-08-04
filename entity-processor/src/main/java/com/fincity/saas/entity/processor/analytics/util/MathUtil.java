package com.fincity.saas.entity.processor.analytics.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MathUtil {

    private static final Map<Class<?>, Supplier<? extends Number>> ZERO_SUPPLIERS = Map.of(
            Integer.class, () -> 0,
            Long.class, () -> 0L,
            Double.class, () -> 0.0,
            Float.class, () -> 0.0f,
            Short.class, () -> (short) 0,
            Byte.class, () -> (byte) 0,
            BigInteger.class, () -> BigInteger.ZERO,
            BigDecimal.class, () -> BigDecimal.ZERO);

    private static final Integer DEFAULT_ZERO = 0;

    public static <K, V extends Number> V sumMapValues(Map<K, V> map) {
        if (map == null || map.isEmpty()) return getDefaultZero();

        V first = map.values().iterator().next();
        return sumWithType(map.values(), first);
    }

    public static <T extends Number> T sumCollection(Collection<T> collection) {
        if (collection == null || collection.isEmpty()) return getDefaultZero();

        T first = collection.iterator().next();
        return sumWithType(collection, first);
    }

    public static <T extends Number> T sumCollection(Collection<T> collection, Class<T> type) {
        if (collection == null || collection.isEmpty()) return getZeroValue(type);

        T first = collection.iterator().next();
        return sumWithType(collection, first);
    }

    public static <T, R extends Number> R sumCollectionWith(Collection<T> collection, Function<T, R> mapper) {
        if (collection == null || collection.isEmpty()) return getDefaultZero();

        R first = mapper.apply(collection.iterator().next());
        return sumWithType(collection.stream().map(mapper).toList(), first);
    }

    public static <T, R extends Number> R sumCollectionWith(
            Collection<T> collection, Function<T, R> mapper, Class<R> returnType) {
        if (collection == null || collection.isEmpty()) return getZeroValue(returnType);

        R first = mapper.apply(collection.iterator().next());
        return sumWithType(collection.stream().map(mapper).toList(), first);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Number> T getZeroValue(Class<T> type) {
        if (type == null) return (T) DEFAULT_ZERO;

        Supplier<? extends Number> supplier = ZERO_SUPPLIERS.get(type);
        return supplier != null ? (T) supplier.get() : (T) DEFAULT_ZERO;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Number> T getDefaultZero() {
        return (T) DEFAULT_ZERO;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Number> T sumWithType(Collection<T> collection, T sample) {
        if (collection.isEmpty())
            return sample instanceof Integer ? (T) DEFAULT_ZERO : getZeroValue((Class<T>) sample.getClass());

        return switch (sample) {
            case Integer ignored ->
                (T) Integer.valueOf(
                        collection.stream().mapToInt(Number::intValue).sum());
            case Long ignored ->
                (T) Long.valueOf(
                        collection.stream().mapToLong(Number::longValue).sum());
            case Float ignored ->
                (T) Float.valueOf((float)
                        collection.stream().mapToDouble(Number::doubleValue).sum());
            case Short ignored ->
                (T) Short.valueOf(
                        (short) collection.stream().mapToInt(Number::intValue).sum());
            case Byte ignored ->
                (T) Byte.valueOf(
                        (byte) collection.stream().mapToInt(Number::intValue).sum());
            case BigInteger ignored ->
                (T) collection.stream()
                        .map(n -> BigInteger.valueOf(n.longValue()))
                        .reduce(BigInteger.ZERO, BigInteger::add);
            case BigDecimal ignored ->
                (T) collection.stream()
                        .map(n -> BigDecimal.valueOf(n.doubleValue()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            case null, default ->
                (T) Double.valueOf(
                        collection.stream().mapToDouble(Number::doubleValue).sum());
        };
    }
}
