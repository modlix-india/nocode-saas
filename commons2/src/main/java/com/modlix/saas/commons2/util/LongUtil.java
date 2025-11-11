package com.modlix.saas.commons2.util;

public class LongUtil {

    public static Long safeValueOf(Object object, Long... defaultValue) {
        if (object == null)
            return null;
        if (object instanceof Long l)
            return l;
        if (object instanceof Number n)
            return n.longValue();
        try {
            return Long.parseLong(object.toString());
        } catch (NumberFormatException e) {
            for (Long eachDefaultValue : defaultValue) {
                if (eachDefaultValue != null)
                    return eachDefaultValue;
            }
            return null;
        }
    }
}
