package com.modlix.saas.commons2.mq.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LogUtilTest {

    @Test
    public void testDebugKey_Constant() {
        // When & Then
        assertEquals("DEBUG", LogUtil.DEBUG_KEY);
    }

    @Test
    public void testLogUtil_Constructor() {
        // When
        LogUtil logUtil = new LogUtil();

        // Then
        assertNotNull(logUtil);
    }

    @Test
    public void testLogUtil_StaticAccess() {
        // When & Then
        assertNotNull(LogUtil.DEBUG_KEY);
        assertEquals("DEBUG", LogUtil.DEBUG_KEY);
    }
}
