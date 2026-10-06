package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLDrift;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.pool.ConnectionPool;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.testcontainers.containers.MySQLContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A client that brings its own database, on a second MySQL server.
 *
 * An appData connection is an overridable document, and
 * {@code ConnectionService.read} accepts one whose clientCode matches as readily as
 * an app-level one - so two clients of the same app can sit on two different
 * servers. Everything that reconciles or inspects tables used to read ONE
 * connection and sweep the schemas on its server, which migrated whoever shared a
 * host with the author and silently left the rest behind: no error, no blocked
 * tenant in the report, just a table quietly on the old shape.
 *
 * It needs two servers to show at all, which is why it went unnoticed. This class
 * starts a second container for the purpose and asserts against the tables on BOTH.
 */
@DisplayName("A tenant on its own MySQL server")
class MySQLBringYourOwnDatabaseIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String STORAGE_NAME = "invoices";
    private static final String TABLE = "testapp_invoices";
    private static final String OTHER = "BYOD";

    /** The second server. The shared one in the base class is the first. */
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> SECOND = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("root_db")
            .withUsername("root")
            .withPassword("test");

    private static DSLContext first;
    private static DSLContext second;

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private StorageService storageService;

    @BeforeEach
    void setUp() {

        // BYOD inherits SYSTEM, so the same definition resolves for both and the
        // two servers are genuinely holding the same storage.
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> Mono.just(
                        OTHER.equals(call.getArgument(2)) ? List.of(SYSTEM, OTHER) : List.of(SYSTEM)));

        first = mysql();
        second = secondCtx();

        // Every schema of the app on both servers, not just the two this test makes.
        // The first server is shared with every other test class in the run, and
        // drift reports one entry per schema it finds there, so a client schema left
        // behind by an earlier class turned up in the deduplication count.
        dropTenants(first);
        dropTenants(second);

        this.givenStorage("STRING", 40);
        this.givenConnections();

        // A table on each server, built by the backend itself rather than by hand,
        // so both start at the shape the definition describes.
        this.givenTable(first, SYSTEM);
        this.givenTable(second, OTHER);
    }

    @AfterAll
    static void stopSecond() {
        if (SECOND.isRunning()) SECOND.stop();
    }

    /** Built the same way the base class builds the first: R2DBC, so one idiom runs both. */
    private static synchronized DSLContext secondCtx() {
        if (second != null) return second;

        SECOND.start();
        second = DSL.using(
                new ConnectionPool(ConnectionPoolConfiguration.builder(ConnectionFactories.get(
                                ConnectionFactoryOptions.builder()
                                        .option(ConnectionFactoryOptions.DRIVER, "pool")
                                        .option(ConnectionFactoryOptions.PROTOCOL, "mysql")
                                        .option(ConnectionFactoryOptions.HOST, SECOND.getHost())
                                        .option(ConnectionFactoryOptions.PORT, SECOND.getFirstMappedPort())
                                        .option(ConnectionFactoryOptions.USER, "root")
                                        .option(ConnectionFactoryOptions.PASSWORD, "test")
                                        .build()))
                        .build()),
                SQLDialect.MYSQL);

        return second;
    }

    private static void dropTenants(DSLContext ctx) {
        List<String> existing = Flux.from(ctx.resultQuery(
                        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE '%\\_"
                                + APP_CODE + "' OR SCHEMA_NAME LIKE '%\\_" + APP_CODE + "_draft'"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();

        for (String db : existing) exec(ctx, "DROP DATABASE IF EXISTS `" + db + "`");
    }

    private static void exec(DSLContext ctx, String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static String secondUrl() {
        secondCtx();
        return "r2dbc:mysql://" + SECOND.getHost() + ":" + SECOND.getFirstMappedPort() + "/root_db";
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorage(String type, int length) {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Map<String, Object> amount = new HashMap<>(Map.of("type", type));
        if ("STRING".equals(type)) amount.put("maxLength", length);

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        storage.setUniqueName(TABLE);
        storage.setSchema(new HashMap<>(
                Map.of("type", "OBJECT", "properties", new HashMap<>(Map.of("amount", amount)))));
        this.insertRaw(storage);
    }

    private void givenConnections() {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Connection.class)
                .block();

        // The app's own, on the first server.
        Connection house = new Connection();
        house.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(
                        new HashMap<>(Map.of("url", mysqlUrl(), "username", "root", "password", "test")));
        house.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        house.setId("0000000000000000byod0001");
        this.insertRaw(house);

        // One client's own, on a server the platform does not otherwise touch.
        Connection byod = new Connection();
        byod.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.FALSE)
                .setConnectionDetails(
                        new HashMap<>(Map.of("url", secondUrl(), "username", "root", "password", "test")));
        byod.setName("appData").setAppCode(APP_CODE).setClientCode(OTHER).setVersion(1);
        byod.setId("0000000000000000byod0002");
        this.insertRaw(byod);
    }

    /** The table as the backend builds it, on whichever server that client is on. */
    private void givenTable(DSLContext ctx, String client) {
        String db = client + "_" + APP_CODE;
        exec(ctx, "CREATE DATABASE IF NOT EXISTS `" + db + "`");
        exec(
                ctx,
                "CREATE TABLE IF NOT EXISTS `" + db + "`.`" + TABLE
                        + "` (`_id` CHAR(26) NOT NULL, `amount` VARCHAR(40) NULL, PRIMARY KEY (`_id`))");
    }

    private static String columnType(DSLContext ctx, String client) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + client + "_" + APP_CODE + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private <T> T asSystem(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    /**
     * Widen the column through a real SAVE, not by handing a mutated object to the
     * reconciler. The planner re-reads the definition per tenant, so an in-memory
     * edit changes nothing - and going through {@code update} exercises the path a
     * publish actually takes, including the cache eviction and the rebuild it
     * chains afterwards.
     */
    private void widenAndReconcile() {
        Storage stored = this.asSystem(this.storageService.read(STORAGE_NAME, APP_CODE, SYSTEM))
                .getObject();

        Map<String, Object> amount = new HashMap<>(Map.of("type", "STRING", "maxLength", 200));
        stored.setSchema(new HashMap<>(
                Map.of("type", "OBJECT", "properties", new HashMap<>(Map.of("amount", amount)))));

        this.asSystem(this.storageService.update(stored));
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Timeout(600)
    @DisplayName("a definition change reaches the tenant on the other server")
    void reconcileCrossesServers() {

        assertEquals("varchar(40)", columnType(first, SYSTEM));
        assertEquals("varchar(40)", columnType(second, OTHER));

        widenAndReconcile();

        assertEquals("varchar(200)", columnType(first, SYSTEM), "the house server was always migrated");
        assertEquals(
                "varchar(200)",
                columnType(second, OTHER),
                "the tenant on its own server used to be skipped silently");
    }

    @Test
    @Timeout(600)
    @DisplayName("drift inspects every server the app uses, not just the first")
    void driftCrossesServers() {

        // Diverge the second server behind the platform's back. Inspecting only the
        // first would report the whole app as in sync.
        exec(second, "ALTER TABLE `" + OTHER + "_" + APP_CODE + "`.`" + TABLE + "` DROP COLUMN `amount`");

        List<MySQLDrift.Report> reports = this.asSystem(this.appDataService.drift(APP_CODE, SYSTEM, STORAGE_NAME));

        MySQLDrift.Report house = reports.stream()
                .filter(r -> r.db().startsWith(SYSTEM + "_"))
                .findFirst()
                .orElse(null);
        MySQLDrift.Report away = reports.stream()
                .filter(r -> r.db().startsWith(OTHER + "_"))
                .findFirst()
                .orElse(null);

        assertNotNull(house, "the house server should be reported: " + reports);
        assertNotNull(away, "the tenant on its own server should be reported too: " + reports);

        assertTrue(house.clean(), house.summary());
        assertTrue(!away.clean(), away.summary());
        assertEquals(1, away.columns().size());
        assertEquals("amount", away.columns().getFirst().column());
    }

    @Test
    @Timeout(600)
    @DisplayName("and a repair puts the other server right")
    void repairCrossesServers() {

        exec(second, "ALTER TABLE `" + OTHER + "_" + APP_CODE + "`.`" + TABLE + "` DROP COLUMN `amount`");

        List<MySQLDrift.DriftRepair> repairs =
                this.asSystem(this.appDataService.repairDrift(APP_CODE, SYSTEM, STORAGE_NAME, false));

        MySQLDrift.DriftRepair away = repairs.stream()
                .filter(r -> r.db().startsWith(OTHER + "_"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, away.applied().size(), "the missing column should have been added back: " + away);
        assertEquals("varchar(40)", columnType(second, OTHER));
    }

    /**
     * The ordinary case must not pay for the rare one. Every client on the
     * platform's own datasource is ONE server, and the sweep has to collapse to one
     * pass rather than running per client.
     */
    @Test
    @Timeout(600)
    @DisplayName("identical connections collapse to a single sweep")
    void duplicateConnectionsAreDeduplicated() {

        Connection twin = new Connection();
        twin.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.FALSE)
                .setConnectionDetails(
                        new HashMap<>(Map.of("url", mysqlUrl(), "username", "root", "password", "test")));
        twin.setName("appData").setAppCode(APP_CODE).setClientCode("TWIN").setVersion(1);
        twin.setId("0000000000000000byod0003");
        this.insertRaw(twin);

        List<MySQLDrift.Report> reports = this.asSystem(this.appDataService.drift(APP_CODE, SYSTEM, STORAGE_NAME));

        long house = reports.stream()
                .filter(r -> r.db().startsWith(SYSTEM + "_"))
                .count();

        assertEquals(1, house, "the house schema must be reported once, not once per connection: " + reports);
    }
}
