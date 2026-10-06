package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.testcontainers.containers.MySQLContainer;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.CoreSchema;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.FanOutReport;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * What happens when a schema change is not safe for everyone.
 *
 * The narrowing path is covered against hand-built tables elsewhere. What those
 * cannot show is the thing that actually matters in production: one client out of
 * three has data that will not convert, and the other two must still get their
 * migration, through the real override chain, the real journal and a real MySQL.
 *
 * Partial completion across a fan-out is a normal state, not a failure, and these
 * tests are the ones that pin what "normal" means.
 */
@DisplayName("A narrowing change across a three-level client chain")
class MySQLTenantMigrationSafetyIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String MID = "LZCLA";
    private static final String LEAF = "LZACP1";

    private static final String STORAGE_NAME = "ledger";
    private static final String TABLE = "testapp_ledger";
    private static final String MONEY = "App.Amount";

    private static final Map<String, List<String>> CHAINS = Map.of(
            SYSTEM, List.of(SYSTEM),
            MID, List.of(SYSTEM, MID),
            LEAF, List.of(SYSTEM, MID, LEAF));

    @Autowired
    private StorageService storageService;

    @Autowired
    private CoreSchemaService coreSchemaService;

    @Autowired
    private AppDataService appDataService;

    private static DSLContext ctx;

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> Mono.just(CHAINS.getOrDefault(call.getArgument(2), List.of(SYSTEM))));

        ctx = mysql();

        for (String client : CHAINS.keySet()) {
            exec("DROP DATABASE IF EXISTS `" + client + "_" + APP_CODE + "`");
            exec("DROP DATABASE IF EXISTS `" + client + "_" + APP_CODE + "_draft`");
        }

        this.givenSchema("STRING", 40);
        this.givenStorages();
        this.givenConnection();
    }

    // ---------------------------------------------------------------- fixtures

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static String columnType(String db, String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private static String liveColumn(String client, String column) {
        return columnType(client + "_" + APP_CODE, column);
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private void givenSchema(String type, Integer maxLength) {

        Map<String, Object> def = new HashMap<>();
        def.put("namespace", "App");
        def.put("name", "Amount");
        def.put("type", type);
        if (maxLength != null) def.put("maxLength", maxLength);

        CoreSchema schema = new CoreSchema();
        schema.setName(MONEY).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        schema.setDefinition(def);

        this.insertRaw(schema);
    }

    private void givenStorages() {

        Storage system = new Storage();
        system.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        system.setUniqueName(TABLE);
        system.setSchema(new HashMap<>(Map.of(
                "type", "OBJECT", "properties", new HashMap<>(Map.of("amount", new HashMap<>(Map.of("ref", MONEY)))))));
        this.insertRaw(system);

        Storage mid = new Storage();
        mid.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(MID)
                .setBaseClientCode(SYSTEM)
                .setVersion(1);
        mid.setUniqueName(TABLE);
        mid.setSchema(new HashMap<>());
        this.insertRaw(mid);

        Storage leaf = new Storage();
        leaf.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(LEAF)
                .setBaseClientCode(MID)
                .setVersion(1);
        leaf.setUniqueName(TABLE);
        leaf.setSchema(new HashMap<>());
        this.insertRaw(leaf);
    }

    private void givenConnection() {

        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "url",
                        mysqlUrl(),
                        "username",
                        "root",
                        "password",
                        "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        // A fixed id so the whole class shares ONE connection pool. Left to Mongo,
        // every @BeforeEach writes a new document, the backend keys its pool cache on
        // the id, and the pools accumulate until MySQL refuses the next connection.
        conn.setId("00000000000000safety0001");

        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {

        String[] storage = allAuthoritiesFor("Storage");
        String[] schema = allAuthoritiesFor("Schema");
        String[] both = new String[storage.length + schema.length];
        System.arraycopy(storage, 0, both, 0, storage.length);
        System.arraycopy(schema, 0, both, storage.length, schema.length);

        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(this.authFor(clientCode, both)))
                .block();
    }

    private void write(String client, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        this.asClient(
                this.appDataService.create(APP_CODE, client, STORAGE_NAME, new DataObject().setData(row), false, null),
                client);
    }

    private void writeOnDraft(String client, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        this.asClient(
                this.onDraftSurface(this.appDataService.create(
                        APP_CODE, client, STORAGE_NAME, new DataObject().setData(row), false, null)),
                client);
    }

    /** Retype the shared schema from a string to a number, which is a narrowing. */
    private void whenSchemaBecomesNumeric() {

        CoreSchema existing = this.asClient(this.coreSchemaService.read(MONEY, APP_CODE, SYSTEM), SYSTEM)
                .getObject();

        CoreSchema edit = new CoreSchema(existing);
        edit.setDefinition(new HashMap<>(Map.of("namespace", "App", "name", "Amount", "type", "DOUBLE")));

        this.asClient(this.coreSchemaService.update(edit), SYSTEM);
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("when one tenant's data will not convert")
    class PartialFanOut {

        @Test
        @Timeout(180)
        @DisplayName("the clean tenants migrate and the dirty one is left exactly as it was")
        void dirtyTenantIsIsolated() {

            write(SYSTEM, "10");
            write(MID, "not a number");
            write(LEAF, "30");

            whenSchemaBecomesNumeric();

            assertEquals("double", liveColumn(SYSTEM, "amount"));
            assertEquals("double", liveColumn(LEAF, "amount"));

            // Not half migrated, not emptied, not dropped. The pre-flight runs before
            // any DDL is issued anywhere, so the blocked tenant is never touched at
            // all rather than stopped part way.
            assertEquals("varchar(40)", liveColumn(MID, "amount"));
            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM `" + MID + "_" + APP_CODE + "`.`" + TABLE
                            + "` WHERE `amount` = 'not a number'"),
                    "the row that blocked the migration is still there to be fixed");
        }

        @Test
        @Timeout(180)
        @DisplayName("a blocked tenant leaves no journal row to clear")
        void blockedTenantIsNotJournalled() {

            write(SYSTEM, "10");
            write(MID, "not a number");

            whenSchemaBecomesNumeric();

            // Attempting it anyway would stop at the same check a moment later, having
            // already written a FAILED row - which then blocks this storage until
            // somebody clears it, for work that was never started.
            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + MID + "_"
                            + APP_CODE + "' AND TABLE_NAME='storage_migration'"));
        }

        @Test
        @Timeout(180)
        @DisplayName("fixing the data and re-running finishes the job")
        void recoveryIsARerun() {

            write(SYSTEM, "10");
            write(MID, "not a number");

            whenSchemaBecomesNumeric();
            assertEquals("varchar(40)", liveColumn(MID, "amount"));

            exec("UPDATE `" + MID + "_" + APP_CODE + "`.`" + TABLE + "` SET `amount` = '42'");

            Storage storage = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM), SYSTEM)
                    .getObject();

            // No decisions first, and nothing to undo. The tenants that are done are
            // recognised as done, and the one that was behind catches up.
            FanOutReport report = asClient(
                    appDataService.reconcileStorageDdl(APP_CODE, storage), SYSTEM);

            assertNotNull(report);
            assertTrue(report.allClean(), report.summary());
            assertEquals("double", liveColumn(MID, "amount"));
            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM `" + MID + "_" + APP_CODE + "`.`" + TABLE
                            + "` WHERE `amount` = 42"),
                    "the fixed value converted rather than being dropped");
        }

        @Test
        @Timeout(180)
        @DisplayName("the report names every blocked tenant, not just the first")
        void reportNamesThemAll() {

            write(SYSTEM, "10");
            write(MID, "bad");
            write(LEAF, "worse");

            whenSchemaBecomesNumeric();

            Storage storage = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM), SYSTEM)
                    .getObject();
            FanOutReport report = asClient(
                    appDataService.reconcileStorageDdl(APP_CODE, storage), SYSTEM);

            // Learning about one bad tenant, fixing it, and then learning about the
            // next is how a 71-way publish turns into a week.
            assertFalse(report.allClean());
            assertTrue(report.blocked().contains(MID + "_" + APP_CODE), report.summary());
            assertTrue(report.blocked().contains(LEAF + "_" + APP_CODE), report.summary());
        }
    }

    @Nested
    @DisplayName("across surfaces")
    class Surfaces {

        @Test
        @Timeout(180)
        @DisplayName("the draft table is migrated as well as the live one")
        void draftIsMigratedToo() {

            write(SYSTEM, "10");
            writeOnDraft(SYSTEM, "20");

            assertEquals("varchar(40)", columnType(SYSTEM + "_" + APP_CODE + "_draft", "amount"));

            whenSchemaBecomesNumeric();

            // Publish is cheap precisely because the two surfaces share table names
            // and nothing is moved. That only holds if both are kept in step.
            assertEquals("double", liveColumn(SYSTEM, "amount"));
            assertEquals("double", columnType(SYSTEM + "_" + APP_CODE + "_draft", "amount"));
        }

        @Test
        @Timeout(180)
        @DisplayName("each surface is journalled as the surface it was")
        void surfaceIsRecorded() {

            write(SYSTEM, "10");
            writeOnDraft(SYSTEM, "20");

            whenSchemaBecomesNumeric();

            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE
                            + "`.`storage_migration` WHERE `surface`='LIVE'"));
            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE
                            + "_draft`.`storage_migration` WHERE `surface`='DRAFT'"),
                    "a row that cannot say which surface it altered is not much of a record");
        }

        @Test
        @Timeout(180)
        @DisplayName("dirty draft data does not hold back the live table")
        void surfacesFailIndependently() {

            write(SYSTEM, "10");
            writeOnDraft(SYSTEM, "scratch value");

            whenSchemaBecomesNumeric();

            // Draft is a sandbox. Letting whatever someone is experimenting with there
            // block the published table would make the draft surface a liability.
            assertEquals("double", liveColumn(SYSTEM, "amount"));
            assertEquals("varchar(40)", columnType(SYSTEM + "_" + APP_CODE + "_draft", "amount"));
        }
    }
}
