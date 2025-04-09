package com.fincity.saas.commons.util;

public class CommonsUtil {

    @SuppressWarnings("unchecked")
    public static <T> T nonNullValue(T... values) {

        for (T eachValue : values) {
            if (eachValue != null) return eachValue;
        }

        return null;
    }

    public static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;

        if (a == null || b == null) return false;

        return a.equals(b);
    }

    private CommonsUtil() {}
}
