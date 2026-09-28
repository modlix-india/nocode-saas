package com.fincity.saas.commons.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.util.StatusPrinter2;

/**
 * Pins the logging configuration that stops a log write from blocking a request thread.
 *
 * Spring Boot's appenders are synchronous, and on a reactive service the caller of log.debug(...)
 * is a Netty event loop. On 2026-09-28 that combination stalled production hard enough for a
 * 500-row lookup to time out at nginx's 60 second ceiling while the host idled at 3% CPU. The
 * logback-spring.xml under test wraps both appenders in AsyncAppender so that can no longer
 * happen.
 *
 * The assertion that matters most is neverBlock. Without it AsyncAppender blocks the caller once
 * its queue is full, which is the same failure arriving 16384 messages later - so a change that
 * quietly drops it would look harmless and would not be.
 */
@DisplayName("Async logging configuration")
class AsyncLoggingConfigTest {

    /**
     * Loads logback-spring.xml into a throwaway LoggerContext rather than the live one, so the
     * test cannot disturb logging for anything else in the suite.
     *
     * Spring normally sets these properties before handing the file to logback. Nothing here
     * depends on their values - they exist so the file resolves cleanly outside a Spring context.
     */
    private static LoggerContext load() throws Exception {

        LoggerContext context = new LoggerContext();

        // Spring's LogbackLoggingSystem stashes the Environment in the logger context under this
        // key, and StructuredLogEncoder reads it back to resolve logging.structured.* . Loading
        // the file without it fails with "Unable to find Spring Environment in logger context",
        // so the test has to stand in for Spring here or it is not testing the real file.
        context.putObject(Environment.class.getName(), new StandardEnvironment());

        context.putProperty("LOG_FILE", "target/test-logs/async-logging-test.log");
        context.putProperty("FILE_LOG_THRESHOLD", "TRACE");
        context.putProperty("CONSOLE_LOG_THRESHOLD", "TRACE");
        context.putProperty("FILE_LOG_CHARSET", "UTF-8");
        context.putProperty("CONSOLE_LOG_CHARSET", "UTF-8");

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(
                AsyncLoggingConfigTest.class.getResourceAsStream("/logback-spring.xml"));

        // Surface any logback warning as test output; a silently half-applied config is exactly
        // the failure mode worth catching here.
        new StatusPrinter2().printInCaseOfErrorsOrWarnings(context);
        return context;
    }

    private static List<Appender<?>> rootAppenders(LoggerContext context) {

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<?>> found = new ArrayList<>();
        for (Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> it = root.iteratorForAppenders();
                it.hasNext(); ) found.add(it.next());
        return found;
    }

    @Test
    @DisplayName("the root logger writes only through async appenders")
    void rootIsFullyAsync() throws Exception {

        List<Appender<?>> appenders = rootAppenders(load());

        assertEquals(2, appenders.size(), "expected exactly the two async appenders, got " + appenders);

        for (Appender<?> appender : appenders)
            assertInstanceOf(
                    AsyncAppender.class,
                    appender,
                    "appender '" + appender.getName() + "' is attached to root synchronously, so a "
                            + "slow write would run on the request thread");
    }

    @Test
    @DisplayName("neverBlock is set, so a full queue drops messages instead of stalling the caller")
    void neverBlockIsSet() throws Exception {

        List<Appender<?>> appenders = rootAppenders(load());

        for (Appender<?> appender : appenders) {
            AsyncAppender async = (AsyncAppender) appender;

            assertTrue(
                    async.isNeverBlock(),
                    "neverBlock is false on '" + async.getName() + "'. AsyncAppender blocks the "
                            + "calling thread once the queue fills, which reintroduces the stall "
                            + "this configuration exists to prevent.");

            assertEquals(
                    0,
                    async.getDiscardingThreshold(),
                    "discardingThreshold must be 0 on '" + async.getName() + "'; the default of "
                            + "20% throws away DEBUG and INFO exactly when an incident fills the queue");

            assertFalse(
                    async.isIncludeCallerData(),
                    "includeCallerData captures a stack trace per event on '" + async.getName() + "'");
        }
    }

    @Test
    @DisplayName("the file appender still emits GELF with logging.structured.json.add applied")
    void fileOutputIsGelfWithAddedFields() throws Exception {

        // The XML names the format literally rather than reading FILE_LOG_STRUCTURED_FORMAT, so
        // this checks the thing that change could plausibly have broken: whether
        // logging.structured.json.add.* is still applied. The Loki `instance_id` label is built
        // from instanceId, so losing it would silently break log labelling rather than fail
        // anything loudly.
        Path log = Path.of("target/test-logs/gelf-check.log");
        Files.createDirectories(log.getParent());
        Files.deleteIfExists(log);

        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(new MapPropertySource(
                        "test", java.util.Map.of("logging.structured.json.add.instanceId", "blue-42")));

        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), env);
        context.putProperty("LOG_FILE", log.toString());
        context.putProperty("FILE_LOG_THRESHOLD", "TRACE");
        context.putProperty("CONSOLE_LOG_THRESHOLD", "OFF");
        context.putProperty("FILE_LOG_CHARSET", "UTF-8");
        context.putProperty("CONSOLE_LOG_CHARSET", "UTF-8");

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(
                AsyncLoggingConfigTest.class.getResourceAsStream("/logback-spring.xml"));

        new StatusPrinter2().print(context);
        System.out.println("DEBUG root appenders: " + rootAppenders(context));

        context.getLogger(Logger.ROOT_LOGGER_NAME).warn("gelf marker line");

        // The whole point of this configuration is that the write does NOT happen on the calling
        // thread, so the file is legitimately empty when the log call returns. Wait for the async
        // worker rather than racing it, then stop the context to close the file cleanly.
        for (int i = 0; i < 60 && Files.size(log) == 0; i++) Thread.sleep(50);
        context.stop();

        String written = Files.readString(log);

        assertTrue(written.contains("\"short_message\""),
                "file output is not GELF; Alloy's JSON parsing and every Loki label depend on it: " + written);
        assertTrue(written.contains("gelf marker line"), "the message itself is missing: " + written);
        assertTrue(written.contains("_level_name"),
                "_level_name is missing; the Loki `level` label is built from it: " + written);
        assertTrue(written.contains("blue-42"),
                "logging.structured.json.add.instanceId was NOT applied, so the Loki `instance_id` "
                        + "label would silently stop working: " + written);
    }

    @Test
    @DisplayName("each async appender actually wraps a real destination")
    void asyncAppendersWrapSomething() throws Exception {

        LoggerContext context = load();

        for (Appender<?> appender : rootAppenders(context)) {
            AsyncAppender async = (AsyncAppender) appender;
            Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> wrapped =
                    async.iteratorForAppenders();

            assertTrue(
                    wrapped.hasNext(),
                    "'" + async.getName() + "' wraps nothing, so its output goes nowhere at all");
            assertNotNull(wrapped.next());
        }
    }
}
