package com.fincity.saas.core.integration;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.MySQLContainer;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * One MySQL for every Spring integration test that needs one.
 *
 * Six classes used to declare their own, with identical settings, because
 * {@code withReuse(true)} does nothing unless the developer has set
 * {@code testcontainers.reuse.enable} in their own properties file. Six servers then
 * start within a minute of each other on top of the shared Mongo, and the
 * commons-core suite showed what that costs: one spurious failure in a test that
 * passes on its own, which is the kind of thing that teaches people to re-run rather
 * than read.
 *
 * Kept off {@link AbstractIntegrationTest} on purpose - most core integration tests
 * have nothing to do with MySQL and should not pay for one.
 */
public abstract class AbstractMySQLSpringIntegrationTest extends AbstractIntegrationTest {

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("root_db")
            .withUsername("root")
            .withPassword("test")
            // Headroom. One server now serves every class in the module, and the
            // backend opens a pool per APP_DATA connection document; the default
            // ceiling of 151 is not a lot to share.
            .withCommand("--max-connections=500");

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

    /** What an APP_DATA connection document points at. */
    protected static String mysqlUrl() {
        mysql();
        return "r2dbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort() + "/root_db";
    }
}
