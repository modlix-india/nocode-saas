package com.modlix.saas.commons2.security.util;

import org.slf4j.Logger;
import org.slf4j.MDC;

public class LogUtil {

    public static final String DEBUG_KEY = "x-debug";
    public static final String METHOD_NAME = "x-method-name";

    private LogUtil() {
    }


    public static void info(Logger logger, String message, Object... args) {

        String dk = MDC.get(DEBUG_KEY);
        if (dk == null) return;

        logger.info("{} - {} {}", dk, message, args);
    }

    public static void error(Logger logger, String message, Object... args) {

        String dk = MDC.get(DEBUG_KEY);
        if (dk == null) return;

        logger.error("{} - {} {}", dk, message, args);
    }

    public static void debug(Logger logger, String message, Object... args) {

        logger.debug("{} {}", message, args);
    }
}
