package com.modlix.saas.commons2.jooq.util;

import java.math.BigInteger;

import org.jooq.types.ULong;

public class ULongUtil {

    public static ULong valueOf(Object o) {

        if (o == null)
            return null;

        if (o instanceof ULong v)
            return v;

        if (o instanceof Long n)
            return ULong.valueOf(n);

        if (o instanceof BigInteger b)
            return ULong.valueOf(b);

        return ULong.valueOf(o.toString());
    }

    private ULongUtil() {
    }
}

