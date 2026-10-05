package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.MySQLContainer;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * One MySQL for every integration test in this package.
 *
 * It used to be one per class, because {@code withReuse(true)} does nothing unless
 * {@code testcontainers.reuse.enable} is set in the developer's own properties file,
 * and it is not. Nine servers then start within a minute of each other, and under
 * that load a container occasionally answers before it is really ready: the suite
 * produced exactly one such failure, in a test that passes on its own.
 *
 * A flaky suite is worse than a slow one, because the next real failure gets
 * explained away. Sharing a static container removes the cause rather than retrying
 * around it, needs no change to anyone's machine, and behaves the same on CI.
 *
 * Tests isolate themselves with a SCHEMA each rather than a server each, which is
 * what the product does with tenants anyway.
 *
 * Pinned to 8.4 because that is what dev, stage and production run (OCI MySQL).
 * It was 8.0, so every MySQL behaviour this suite asserts was being checked
 * against a version no environment actually uses.
 */
public abstract class AbstractMySQLIntegrationTest {

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("root_db")
            .withUsername("root")
            .withPassword("test");

    private static DSLContext ctx;

    /** Started once, on first use, and shared by every subclass in the JVM. */
    protected static synchronized DSLContext mysql() {

        if (ctx != null) return ctx;

        MYSQL.start();

        ctx = DSL.using(
                new ConnectionPool(ConnectionPoolConfiguration.builder(ConnectionFactories.get(
                                ConnectionFactoryOptions.builder()
                                        .option(ConnectionFactoryOptions.DRIVER, "pool")
                                        .option(ConnectionFactoryOptions.PROTOCOL, "mysql")
                                        .option(ConnectionFactoryOptions.HOST, MYSQL.getHost())
                                        .option(ConnectionFactoryOptions.PORT, MYSQL.getFirstMappedPort())
                                        .option(ConnectionFactoryOptions.USER, "root")
                                        .option(ConnectionFactoryOptions.PASSWORD, "test")
                                        .build()))
                        .build()),
                SQLDialect.MYSQL);

        return ctx;
    }

    protected static String mysqlHost() {
        mysql();
        return MYSQL.getHost();
    }

    protected static int mysqlPort() {
        mysql();
        return MYSQL.getFirstMappedPort();
    }

    /** A tenant schema of this test's own. */
    protected static void schema(String name) {
        Mono.from(mysql().query("CREATE DATABASE IF NOT EXISTS `" + name + "`")).block();
    }
}
