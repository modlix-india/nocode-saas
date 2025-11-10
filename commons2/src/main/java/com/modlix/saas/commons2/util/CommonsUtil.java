package com.modlix.saas.commons2.util;

import lombok.NonNull;

public class CommonsUtil {

    public static <T> @NonNull T colease(T value1, T value2) {
        if (value1 != null) return value1;
        if (value2 != null) return value2;
        throw new NullPointerException();
    }

    public static <T> @NonNull T colease(T value1, T value2, T value3) {
        if (value1 != null) return value1;
        if (value2 != null) return value2;
        if (value3 != null) return value3;
        throw new NullPointerException();
    }

    @SuppressWarnings("unchecked")
    public static <T> T nonNullValue(T... values) {

        for (T eachValue : values) {
            if (eachValue != null)
                return eachValue;
        }

        return null;
    }

    public static boolean safeEquals(Object a, Object b) {
        if (a == b)
            return true;

        if (a == null || b == null)
            return false;

        return a.equals(b);
    }

    private CommonsUtil() {
    }
}
