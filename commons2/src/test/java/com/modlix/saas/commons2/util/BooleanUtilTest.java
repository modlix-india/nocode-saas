package com.modlix.saas.commons2.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BooleanUtilTest {

    @Test
    void test() {

        Boolean result1 = BooleanUtil.safeValueOfWithEmpty(true);
        assertTrue(result1);

        Boolean result2 = BooleanUtil.safeValueOfWithEmpty(false);
        assertNull(result2);

    }

}
