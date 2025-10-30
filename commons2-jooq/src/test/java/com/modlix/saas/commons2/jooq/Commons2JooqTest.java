package com.modlix.saas.commons2.jooq;

import static org.junit.jupiter.api.Assertions.*;

import com.modlix.saas.commons2.jooq.util.ULongUtil;
import com.modlix.saas.commons2.util.Tuples;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.junit.jupiter.api.Test;

public class Commons2JooqTest {

    @Test
    public void testULongUtilValueOf() {
        // Test null
        assertNull(ULongUtil.valueOf(null));

        // Test ULong instance
        ULong uLong = ULong.valueOf(123L);
        assertEquals(uLong, ULongUtil.valueOf(uLong));

        // Test Long
        assertEquals(ULong.valueOf(456L), ULongUtil.valueOf(456L));

        // Test String
        assertEquals(ULong.valueOf(789L), ULongUtil.valueOf("789"));

        // Test BigInteger
        java.math.BigInteger bigInt = new java.math.BigInteger("999");
        assertEquals(ULong.valueOf(bigInt), ULongUtil.valueOf(bigInt));
    }

    @Test
    public void testULongCreation() {
        ULong uLong = ULong.valueOf(123L);
        assertNotNull(uLong);
        assertEquals(123L, uLong.longValue());
    }

    @Test
    public void testUIntegerCreation() {
        UInteger uInteger = UInteger.valueOf(456);
        assertNotNull(uInteger);
        assertEquals(456, uInteger.intValue());
    }

    @Test
    public void testUShortCreation() {
        UShort uShort = UShort.valueOf((short) 789);
        assertNotNull(uShort);
        assertEquals(789, uShort.shortValue());
    }

    @Test
    public void testTuple2Creation() {
        // Test the internal Tuple2 class
        Tuples.Tuple2<String, Integer> tuple = new Tuples.Tuple2<>("test", 123);
        assertEquals("test", tuple.getT1());
        assertEquals(123, tuple.getT2());
    }
}
