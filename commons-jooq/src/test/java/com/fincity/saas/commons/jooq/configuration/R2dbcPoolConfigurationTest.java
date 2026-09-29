package com.fincity.saas.commons.jooq.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.r2dbc.pool.ConnectionPool;

/**
 * Pins the one thing that was silently untrue for as long as this class has existed: that
 * {@code spring.r2dbc.pool.*} sizes the pool the service actually queries through.
 *
 * <p>Before 2026-09-29 it did not. commons-jooq built its own pool, never read the properties, and
 * ran on r2dbc-pool's default of 10 while Spring Boot built a second, correctly configured pool
 * that nothing used. Nothing failed - the setting was simply inert, which is why it survived. On
 * production the unused pool sat at its configured 20 with zero waiters while the real one capped
 * at 10 and queued 60.
 *
 * <p>A test is the only thing that catches this class of bug, because the failure mode is a
 * configuration value having no effect, and that looks identical to a configuration value being
 * correct until the day you need the headroom.
 */
class R2dbcPoolConfigurationTest {

    /** Concrete stand-in; the base class is abstract and needs only an ObjectMapper. */
    private static class TestConfiguration extends AbstractJooqBaseConfiguration {

        TestConfiguration() {
            super(new ObjectMapper());
            // Normally injected by @Value. No connection is opened here: ConnectionPool builds
            // lazily and only warms up on first acquire, so this needs no database.
            this.url = "r2dbc:mysql://localhost:3306/commons_jooq_test";
            this.username = "test";
            this.password = "test";
        }
    }

    private static R2dbcProperties propertiesWithMaxSize(int maxSize, int initialSize) {
        R2dbcProperties properties = new R2dbcProperties();
        properties.getPool().setMaxSize(maxSize);
        properties.getPool().setInitialSize(initialSize);
        properties.getPool().setMaxIdleTime(Duration.ofMinutes(30));
        return properties;
    }

    @Test
    @DisplayName("maxSize comes from spring.r2dbc.pool.max-size, not r2dbc-pool's default of 10")
    void poolIsSizedFromSpringProperties() {

        ConnectionPool pool = new TestConfiguration().connectionFactory(propertiesWithMaxSize(17, 3));

        assertTrue(pool.getMetrics().isPresent(), "pool must expose metrics, or the dashboards go blank");
        assertEquals(
                17,
                pool.getMetrics().orElseThrow().getMaxAllocatedSize(),
                "pool took r2dbc-pool's default instead of the configured max-size - the exact bug this pins");
    }

    @Test
    @DisplayName("a different configured size produces a different pool, so the value is really read")
    void sizeTracksTheProperty() {

        // Guards against an assertion that would pass on a hardcoded 17.
        ConnectionPool pool = new TestConfiguration().connectionFactory(propertiesWithMaxSize(42, 1));

        assertEquals(42, pool.getMetrics().orElseThrow().getMaxAllocatedSize());
    }

    @Test
    @DisplayName("the pool is not wrapped around another pool")
    void poolIsNotNested() {

        // The old code forced DRIVER=pool, so ConnectionFactories returned a ConnectionPool that
        // was then wrapped in a second one. jOOQ acquired from the outer, which acquired from the
        // inner, and each defaulted to 10 independently. The factory underneath must be the raw
        // driver, never another pool.
        ConnectionPool pool = new TestConfiguration().connectionFactory(propertiesWithMaxSize(7, 1));

        Object inner = pool.unwrap();
        assertTrue(
                !(inner instanceof ConnectionPool),
                "the underlying ConnectionFactory is itself a pool - the nesting is back");
    }
}
