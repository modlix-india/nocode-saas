package com.fincity.saas.commons.jooq.util;

import java.math.BigInteger;

import org.jooq.types.ULong;

public class ULongUtil {

    private ULongUtil() {
    }

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

    public static ULong valueOfDouble(Object o) {

        if (o == null)
            return null;

        if (o instanceof ULong v)
            return v;

        if (o instanceof Double n)
            return ULong.valueOf(n.longValue());

        if (o instanceof Float n)
            return ULong.valueOf(n.longValue());

        return ULong.valueOf(o.toString().split("\\.")[0]);
    }
}
