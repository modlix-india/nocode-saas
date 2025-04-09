package com.fincity.saas.commons.util;

public class EqualsUtil {

    public static <T> boolean safeEquals(T a, T b) {

        if (a == b) return true;

        if (a != null) return a.equals(b);

        return false;
    }

    private EqualsUtil() {}
}
