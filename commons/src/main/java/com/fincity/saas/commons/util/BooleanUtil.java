package com.fincity.saas.commons.util;

import reactor.core.publisher.Mono;

public class BooleanUtil {

    private static final Byte BYTE_1 = Byte.valueOf((byte) 1);

    public static boolean safeValueOf(Object object) {
        boolean returnValue = false;

        if (object == null)
            return false;

        if (object instanceof Boolean b)
            return b;

        if (object instanceof Byte)
            return BYTE_1.equals(object);

        String value = object.toString();

        if (!value.isBlank()) {
            if ("yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                    || "t".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
                returnValue = true;
            } else {
                try {
                    int val = Integer.parseInt(value);
                    if (val != 0) {
                        returnValue = true;
                    }
                } catch (NumberFormatException nfe) {
                    return returnValue;
                }
            }
        }
        return returnValue;
    }

    public static Mono<Boolean> safeValueOfWithEmpty(Object b) {
        return Mono.just(safeValueOf(b)).filter(Boolean::booleanValue);
    }

    private BooleanUtil() {
    }
}
