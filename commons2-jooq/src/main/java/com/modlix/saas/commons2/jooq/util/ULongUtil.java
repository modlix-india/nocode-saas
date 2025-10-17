package com.modlix.saas.commons2.jooq.util;

import java.math.BigInteger;
import org.jooq.types.ULong;

public class ULongUtil {

    public static ULong valueOf(Object o) {

        switch (o) {
            case null -> {
                return null;
            }
            case ULong v -> {
                return v;
            }
            case Long n -> {
                return ULong.valueOf(n);
            }
            case BigInteger b -> {
                return ULong.valueOf(b);
            }
            case Number n -> {
                return ULong.valueOf(n.longValue());
            }
            default -> {
                try {
                    return ULong.valueOf(o.toString());
                } catch (Exception ex) {
                    return ULong.valueOf(Double.valueOf(o.toString()).longValue());
                }
            }
        }
    }

    private ULongUtil() {}
}
