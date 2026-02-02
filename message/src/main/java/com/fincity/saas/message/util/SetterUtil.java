package com.fincity.saas.message.util;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.util.MultiValueMap;

@UtilityClass
public final class SetterUtil {

    public static void setIfPresent(MultiValueMap<String, String> map, String key, Consumer<String> setter) {
        String value = map.getFirst(key);
        if (value != null) setter.accept(value);
    }

    public static void parseLongIfPresent(MultiValueMap<String, String> map, String key, LongConsumer setter) {
        String value = map.getFirst(key);
        if (value != null)
            try {
                setter.accept(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
            }
    }

    public static void parseIntegerIfPresent(MultiValueMap<String, String> map, String key, IntConsumer setter) {
        String value = map.getFirst(key);
        if (value != null)
            try {
                setter.accept(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
    }

    public static <E extends Enum<E>> void parseEnumIfPresent(
            MultiValueMap<String, String> map,
            String key,
            Class<E> enumClass,
            Predicate<E> matchPredicate,
            Consumer<E> setter) {
        String value = map.getFirst(key);
        parseEnum(value, enumClass, matchPredicate, setter);
    }

    public static <E extends Enum<E>> void parseEnum(
            String value, Class<E> enumClass, Predicate<E> matchPredicate, Consumer<E> setter) {
        if (value == null) return;

        for (E constant : enumClass.getEnumConstants()) {
            if (matchPredicate.test(constant)) {
                setter.accept(constant);
                return;
            }
        }
    }

    public static void setIfPresent(Map<String, String> map, String key, Consumer<String> setter) {
        Object value = map.get(key);
        if (value != null) setter.accept(value.toString());
    }

    public static <T> void setIfPresent(T value, Consumer<T> consumer) {
        if (value != null && (value.getClass() != String.class || !((String) value).isEmpty())) {
            consumer.accept(value);
        }
    }

    public static Long parseLong(Object obj) {
        if (obj instanceof Number number) return number.longValue();
        try {
            return obj != null ? Long.parseLong(obj.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
