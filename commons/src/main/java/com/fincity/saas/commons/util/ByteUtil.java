package com.fincity.saas.commons.util;

public class ByteUtil {

    public static final Byte ONE = Byte.valueOf((byte) 1);
    public static final Byte ZERO = Byte.valueOf((byte) 0);

    private ByteUtil() {}

    public static Byte toByte(boolean value) {
        return value ? ONE : ZERO;
    }
}
