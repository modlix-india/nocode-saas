package com.fincity.saas.commons.jooq.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.net.ConnectException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What happens to the pool when the database dies underneath it.
 *
 * Measured on 2026-10-04 after MySQL went down under a running core and security.
 * Three things turned out to be true, and only the first was expected:
 *
 * <ol>
 * <li>{@code validationQuery} does NOT prevent the first failure. The dead socket
 * surfaces as netty's {@code IllegalStateException: channel not registered to an
 * event loop} thrown from inside validation, so it propagates to the caller
 * instead of marking the connection invalid.</li>
 * <li>The pool DOES heal itself - a failed acquire discards that connection - so
 * the damage is bounded by the number of stale connections, not permanent. A
 * service restart was never actually required, which is what we assumed at the
 * time and got wrong.</li>
 * <li>A server that is genuinely gone reports {@code ConnectException}, not
 * {@code IllegalStateException}, so the two are cleanly separable and only the
 * first is worth retrying.</li>
 * </ol>
 *
 * Opt-in: needs a throwaway MySQL on 13306 and the docker CLI.
 *   docker run -d --name r2dbcprobe -e MYSQL_ROOT_PASSWORD=probe \
 *     -e MYSQL_DATABASE=probe -p 13306:3306 mysql:8.0
 *   mvn test -Dtest=PoolRecoveryAfterServerDeathTest -Dr2dbc.probe=true
 *
 * Uses {@code docker kill}, not {@code docker stop}: a graceful stop closes
 * sockets and any pool copes. The failure we hit was an abrupt death.
 */
@EnabledIfSystemProperty(named = "r2dbc.probe", matches = "true")
class PoolRecoveryAfterServerDeathTest {

    private static final String URL = "r2dbc:mysql://localhost:13306/probe";
    private static final String CONTAINER = "r2dbcprobe";

    private static ConnectionPool pool() {
        return AbstractJooqBaseConfiguration.pool(
                AbstractJooqBaseConfiguration.driverFactory(URL, "root", "probe"),
                4,
                8,
                Duration.ofMinutes(30),
                "SELECT 1");
    }

    private static Integer selectOne(ConnectionFactory factory) {
        return Mono.usingWhen(
                        factory.create(),
                        c -> Flux.from(c.createStatement("SELECT 1").execute())
                                .flatMap(r -> r.map((row, meta) -> row.get(0, Integer.class)))
                                .next(),
                        Connection::close)
                .block(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("No request fails when the server comes back, without restarting anything")
    void survivesAbruptServerDeath() throws Exception {

        ConnectionPool pool = pool();
        ConnectionFactory factory = AbstractJooqBaseConfiguration.recovering(pool);

        for (int i = 0; i < 8; i++) assertEquals(1, selectOne(factory));

        run("docker", "kill", CONTAINER);
        run("docker", "start", CONTAINER);
        waitForMysql();

        assertEquals(
                1,
                selectOne(factory),
                "a stale pooled connection reached the caller - the retry is not covering it");

        pool.dispose();
    }

    /**
     * The retry must not turn an outage into a hang. If this ever starts passing
     * slowly, the filter has widened to catch ConnectException and every caller is
     * now waiting through retries for a server that is not coming back.
     */
    @Test
    @DisplayName("A server that is really down still fails fast")
    void doesNotRetryARealOutage() throws Exception {

        ConnectionPool pool = pool();
        ConnectionFactory factory = AbstractJooqBaseConfiguration.recovering(pool);
        assertEquals(1, selectOne(factory));

        run("docker", "kill", CONTAINER);
        try {
            long start = System.currentTimeMillis();
            assertThrows(Exception.class, () -> selectOne(factory));
            assertTrue(
                    System.currentTimeMillis() - start < 5_000,
                    "an unreachable server should fail fast, not be retried");
        } finally {
            run("docker", "start", CONTAINER);
            waitForMysql();
            pool.dispose();
        }
    }

    @Test
    @DisplayName("Only the dead-socket signal is retryable, never a refused connection")
    void retryFilterIsNarrow() {

        assertTrue(AbstractJooqBaseConfiguration.isStaleChannel(
                new RuntimeException(new IllegalStateException("channel not registered to an event loop"))));

        assertInstanceOf(ConnectException.class, new ConnectException("Connection refused"));
        assertTrue(!AbstractJooqBaseConfiguration.isStaleChannel(
                new RuntimeException(new ConnectException("Connection refused"))));
    }

    private static void run(String... cmd) throws Exception {
        new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();
    }

    private static void waitForMysql() throws Exception {
        for (int i = 0; i < 60; i++) {
            Process p = new ProcessBuilder(
                            "docker", "exec", CONTAINER, "mysqladmin", "ping", "-uroot", "-pprobe", "--silent")
                    .redirectErrorStream(true)
                    .start();
            if (p.waitFor() == 0) {
                Thread.sleep(500);
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("probe mysql did not come back");
    }
}
