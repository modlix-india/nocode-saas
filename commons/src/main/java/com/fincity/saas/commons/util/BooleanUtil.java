package com.fincity.saas.commons.util;

import javax.annotation.Nullable;

import org.springframework.expression.ParseException;

import reactor.core.publisher.Mono;

public class BooleanUtil {

    private static final Byte BYTE_0 = (byte) 0;

    public static boolean safeValueOf(Object object) {

        if (object == null)
            return false;

        if (object instanceof Boolean b)
            return b;

        if (object instanceof Byte)
            return !BYTE_0.equals(object);

        String value = object.toString();

        boolean returnValue = false;

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

    @Nullable
    public static Boolean parse(Object object) {

        if (object == null)
            return null;

        if (object instanceof Boolean b)
            return b;

        if (object instanceof Byte)
            return !BYTE_0.equals(object);

        String value = object.toString();

        if ("true".equalsIgnoreCase(value))
            return true;

        if ("false".equalsIgnoreCase(value))
            return false;

        throw new ParseException(0, object + " - Not a boolean value to parse");
    }

    public static Mono<Boolean> safeValueOfWithEmpty(Object b) {
        return Mono.justOrEmpty(safeValueOf(b)).filter(Boolean::booleanValue);
    }

    private BooleanUtil() {
    }
}
