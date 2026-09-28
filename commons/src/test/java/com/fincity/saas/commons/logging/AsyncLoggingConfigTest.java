package com.fincity.saas.commons.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

/**
 * Pins logback-spring.xml, which stops a log write from blocking a request thread.
 *
 * Spring Boot's appenders are synchronous, and on a reactive service the caller of log.debug(...)
 * is a Netty event loop. On 2026-09-28 that combination stalled production hard enough for a
 * 500-row lookup to time out at nginx's 60 second ceiling while the host idled at 3% CPU.
 *
 * WHY THIS BOOTS SPRING rather than handing the file to a JoranConfigurator. A raw Joran load
 * looks like it works - the appender graph builds, the log file is even created - and then writes
 * nothing at all, because Spring's LoggingApplicationListener is what actually drives the system.
 * A first version of this test did exactly that, concluded the config was broken, and was wrong.
 * Booting a real (if empty) Spring context is the only way to test the path production uses.
 */
@DisplayName("Async logging configuration")
class AsyncLoggingConfigTest {

    /** An empty context. No auto-configuration, so nothing tries to reach a database. */
    @SpringBootConfiguration
    static class TestApp {}

    private record Observed(List<Appender<?>> rootAppenders, String fileContent) {}

    /**
     * Boots Spring with logging pointed at a scratch file, logs one marker, and returns what came
     * out. Cloud config and discovery are off: this module carries spring-cloud-starter-config, so
     * a bare context otherwise fails with "No spring.config.import set" long before logging
     * matters.
     */
    private static Observed boot(String marker) throws Exception {

        Path log = Path.of("target/test-logs/async-logging-test.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        Files.deleteIfExists(log);

        SpringApplication app = new SpringApplication(TestApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "logging.file.name", log.toString(),
                "logging.structured.json.add.instanceId", "blue-42",
                "spring.main.banner-mode", "off",
                "spring.cloud.config.enabled", "false",
                "spring.cloud.discovery.enabled", "false"));

        try (ConfigurableApplicationContext ignored = app.run()) {

            LoggerFactory.getLogger("com.fincity.probe").warn(marker);

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            List<Appender<?>> appenders = new ArrayList<>();
            for (Iterator<Appender<ILoggingEvent>> it =
                            context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
                    it.hasNext(); ) appenders.add(it.next());

            // The write deliberately does NOT happen on this thread, so the file is legitimately
            // empty when warn() returns. Wait for the worker rather than racing it.
            for (int i = 0; i < 100 && !Files.readString(log).contains(marker); i++)
                Thread.sleep(50);

            return new Observed(appenders, Files.readString(log));
        }
    }

    @Test
    @DisplayName("root logs only through async appenders, and they never block the caller")
    void rootIsAsyncAndNeverBlocks() throws Exception {

        List<Appender<?>> appenders = boot("async-wiring-marker").rootAppenders();

        assertEquals(2, appenders.size(), "expected exactly the two async appenders, got " + appenders);

        for (Appender<?> appender : appenders) {

            AsyncAppender async = assertInstanceOf(
                    AsyncAppender.class,
                    appender,
                    "'" + appender.getName() + "' is attached to root synchronously, so a slow "
                            + "write would run on the request thread");

            assertTrue(
                    async.isNeverBlock(),
                    "neverBlock is false on '" + async.getName() + "'. AsyncAppender blocks the "
                            + "caller once its queue fills, which is the same stall this file "
                            + "exists to prevent, arriving 16384 messages later.");

            assertEquals(
                    0,
                    async.getDiscardingThreshold(),
                    "discardingThreshold must be 0 on '" + async.getName() + "'; the default of 20% "
                            + "drops DEBUG and INFO exactly when an incident fills the queue");

            assertFalse(
                    async.isIncludeCallerData(),
                    "includeCallerData captures a stack trace per event on '" + async.getName() + "'");
        }
    }

    @Test
    @DisplayName("the file is GELF and still carries logging.structured.json.add fields")
    void fileIsGelfWithAddedFields() throws Exception {

        // The format is named literally in the XML rather than read from
        // FILE_LOG_STRUCTURED_FORMAT, so this checks what that could plausibly have broken:
        // whether logging.structured.json.add.* is still applied. Alloy builds the Loki
        // `instance_id` label from instanceId and `level` from _level_name, so losing either
        // breaks log labelling silently rather than failing anything loudly.
        String content = boot("gelf-content-marker").fileContent();

        assertTrue(content.contains("gelf-content-marker"), "the logged line never reached the file");
        assertTrue(
                content.contains("\"short_message\""),
                "file output is not GELF; Alloy's JSON parsing and every Loki label depend on it");
        assertTrue(
                content.contains("_level_name"),
                "_level_name is missing; the Loki `level` label is built from it");
        assertTrue(
                content.contains("blue-42"),
                "logging.structured.json.add.instanceId was not applied, so the Loki `instance_id` "
                        + "label would silently stop working");
    }
}
