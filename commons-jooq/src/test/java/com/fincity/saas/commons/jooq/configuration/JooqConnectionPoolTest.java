package com.fincity.saas.commons.jooq.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pool every JOOQ service talks to MySQL through.
 *
 * Two faults lived here until 2026-10-04 and neither announced itself. The pool was
 * built with {@code ConnectionPoolConfiguration.builder(factory).build()} - no size,
 * no idle time, no validation - so every {@code spring.r2dbc.pool.*} value in every
 * service's yml was dead config. And the factory it wrapped was itself built with
 * {@code DRIVER=pool}, so there were two nested pools.
 *
 * These assertions are on the built objects rather than on a live database, so they
 * run anywhere: r2dbc-pool allocates lazily, and PoolMetrics reports the configured
 * ceiling without a connection ever being opened.
 */
class JooqConnectionPoolTest {

    private static final String URL = "r2dbc:mysql://localhost:3306/core?serverTimezone=UTC";

    @Test
    @DisplayName("The driver factory is NOT itself a pool, so nothing is pooled twice")
    void driverFactoryIsNotAPool() {

        ConnectionFactory factory = AbstractJooqBaseConfiguration.driverFactory(URL, "root", "pw");

        assertFalse(
                factory instanceof ConnectionPool,
                "DRIVER=pool would make this a ConnectionPool, and wrapping it again nests two pools");
    }

    @Test
    @DisplayName("maxSize comes from configuration, not from r2dbc-pool's default of 10")
    void appliesConfiguredMaxSize() {

        ConnectionPool pool = AbstractJooqBaseConfiguration.pool(
                AbstractJooqBaseConfiguration.driverFactory(URL, "root", "pw"),
                3,
                37,
                Duration.ofMinutes(30),
                "SELECT 1");

        assertEquals(
                37,
                pool.getMetrics().orElseThrow().getMaxAllocatedSize(),
                "a configured max-size must reach the pool; 10 means the yml is being ignored again");
    }

    @Test
    @DisplayName("A pool is still built when validation is switched off")
    void toleratesBlankValidationQuery() {

        ConnectionPool pool = AbstractJooqBaseConfiguration.pool(
                AbstractJooqBaseConfiguration.driverFactory(URL, "root", "pw"),
                1,
                5,
                Duration.ofMinutes(1),
                "   ");

        assertInstanceOf(ConnectionPool.class, pool);
        assertEquals(5, pool.getMetrics().orElseThrow().getMaxAllocatedSize());
    }

    @Test
    @DisplayName("The wrapped factory is the configured pool, which is what DSL.using receives")
    void poolIsTheOuterFactory() {

        ConnectionPool pool = AbstractJooqBaseConfiguration.pool(
                AbstractJooqBaseConfiguration.driverFactory(URL, "root", "pw"),
                2,
                11,
                Duration.ofMinutes(5),
                "SELECT 1");

        assertTrue(pool.getMetrics().isPresent(), "no metrics means nothing to alert on when connections run out");
        assertEquals(11, pool.getMetrics().orElseThrow().getMaxAllocatedSize());
    }
}
