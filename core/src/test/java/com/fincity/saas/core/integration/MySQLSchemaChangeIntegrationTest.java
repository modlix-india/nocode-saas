package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * A schema is edited and nobody touched the storage. Three clients, three tables,
 * three different right answers.
 *
 * Every other MySQL test either stops at the generated SQL or starts from merged
 * shapes handed to it. This one goes the whole way: real Mongo documents in the
 * stored delta form, the real override fold, the real reference resolver, the real
 * fan-out, and a real MySQL to land on. It exists because the pieces were each
 * correct on their own while the path between them was not, and nothing short of
 * running it end to end would have shown that.
 *
 * The chain is SYSTEM -> MID -> LEAF, matching how clients actually nest. LEAF pins
 * its own type for the field the schema defines, so the correct outcome is not "all
 * three tables change" - it is two changing and one deliberately not.
 */
@DisplayName("A schema change rebuilds each client's MySQL table")
class MySQLSchemaChangeIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String MID = "LZCLA";
    private static final String LEAF = "LZACP1";

    private static final String STORAGE_NAME = "orders";
    private static final String TABLE = "testapp_orders";
    private static final String MONEY = "App.Money";

    /** base-first inheritance, exactly as security's client graph would report it. */
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

        // The base class stubs one chain for every client. Three levels need three
        // answers: resolving LEAF against SYSTEM's chain would silently drop both
        // overrides and give LEAF the base's table.
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> Mono.just(CHAINS.getOrDefault(call.getArgument(2), List.of(SYSTEM))));

        ctx = mysql();

        for (String client : CHAINS.keySet()) {
            exec("DROP DATABASE IF EXISTS `" + client + "_" + APP_CODE + "`");
            exec("DROP DATABASE IF EXISTS `" + client + "_" + APP_CODE + "_draft`");
        }

        this.givenSchema(40);
        this.givenStorages();
        this.givenConnection();
    }

    // ---------------------------------------------------------------- fixtures

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static String columnType(String client, String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + client + "_" + APP_CODE + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='" + column
                        + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private CoreSchema givenSchema(int maxLength) {

        CoreSchema schema = new CoreSchema();
        schema.setName(MONEY).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        schema.setDefinition(new HashMap<>(Map.of(
                "namespace", "App", "name", "Money", "type", "STRING", "maxLength", maxLength)));

        return this.insertRaw(schema);
    }

    /**
     * Stored in the delta form, which is what the override fold actually reads.
     *
     * Going through create() would re-extract the delta against the very chain under
     * test, so the fixture would be shaped by the code it is meant to check.
     */
    private void givenStorages() {

        Storage system = new Storage();
        system.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        system.setUniqueName(TABLE);
        system.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of(
                        // The field whose type lives in a SCHEMA, not here.
                        "amount", new HashMap<>(Map.of("ref", MONEY)),
                        "note", new HashMap<>(Map.of("type", "STRING", "maxLength", 30)))))));
        this.insertRaw(system);

        // MID adds nothing. It must still be migrated: it inherits the reference and
        // therefore inherits the change.
        Storage mid = new Storage();
        mid.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(MID)
                .setBaseClientCode(SYSTEM)
                .setVersion(1);
        mid.setUniqueName(TABLE);
        mid.setSchema(new HashMap<>());
        this.insertRaw(mid);

        // LEAF pins its own width for the same field. The schema changing underneath
        // must NOT move it.
        Storage leaf = new Storage();
        leaf.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(LEAF)
                .setBaseClientCode(MID)
                .setVersion(1);
        leaf.setUniqueName(TABLE);
        leaf.setSchema(new HashMap<>(Map.of(
                "properties",
                new HashMap<>(Map.of("amount", new HashMap<>(Map.of("type", "STRING", "maxLength", 10)))))));
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
        conn.setId("00000000000schemachange1");

        this.insertRaw(conn);
    }

    /** Both object names: this test edits a Schema and expects a Storage to follow. */
    private <T> T asClient(Mono<T> mono, String clientCode) {

        String[] storage = allAuthoritiesFor("Storage");
        String[] schema = allAuthoritiesFor("Schema");
        String[] both = new String[storage.length + schema.length];
        System.arraycopy(storage, 0, both, 0, storage.length);
        System.arraycopy(schema, 0, both, storage.length, schema.length);

        ContextAuthentication ca = this.authFor(clientCode, both);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    /** One write per client, which is what makes each tenant's table exist. */
    private void givenTablesForEveryClient() {
        for (String client : List.of(SYSTEM, MID, LEAF)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("amount", "10");
            row.put("note", "seed");
            this.asClient(
                    this.appDataService.create(APP_CODE, client, STORAGE_NAME, new DataObject().setData(row), false,
                            null),
                    client);
        }
    }

    private void whenSchemaChangesTo(int maxLength) {

        CoreSchema existing = this.asClient(this.coreSchemaService.read(MONEY, APP_CODE, SYSTEM), SYSTEM)
                .getObject();

        CoreSchema edit = new CoreSchema(existing);
        edit.setDefinition(new HashMap<>(Map.of(
                "namespace", "App", "name", "Money", "type", "STRING", "maxLength", maxLength)));

        this.asClient(this.coreSchemaService.update(edit), SYSTEM);
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("before anything changes")
    class Baseline {

        @Test
        @Timeout(180)
        @DisplayName("each client gets the table its own merged definition asks for")
        void threeClientsThreeTables() {

            givenTablesForEveryClient();

            // SYSTEM and MID both resolve amount through the schema reference. Were
            // the reference not resolved, this column would be JSON and the whole
            // point of being on MySQL would be gone.
            assertEquals("varchar(40)", columnType(SYSTEM, "amount"));
            assertEquals("varchar(40)", columnType(MID, "amount"));

            // LEAF pinned its own width beside the reference.
            assertEquals("varchar(10)", columnType(LEAF, "amount"));

            // And the field that was never a reference is the same everywhere.
            for (String client : List.of(SYSTEM, MID, LEAF))
                assertEquals("varchar(30)", columnType(client, "note"));
        }

        @Test
        @Timeout(180)
        @DisplayName("a row written through the service comes back")
        void writeAndRead() {

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("amount", "25");
            row.put("note", "hello");

            Map<String, Object> created = asClient(
                    appDataService.create(APP_CODE, MID, STORAGE_NAME, new DataObject().setData(row), false, null),
                    MID);

            assertNotNull(created);
            assertEquals("25", created.get("amount"));
            assertNotNull(created.get("_id"), "the backend mints a ULID rather than an auto-increment key");
        }
    }

    @Nested
    @DisplayName("when the referenced schema changes")
    class SchemaEdited {

        @Test
        @Timeout(180)
        @DisplayName("every client that inherits the reference is migrated, and the one that pinned is not")
        void widensInheritorsOnly() {

            givenTablesForEveryClient();
            whenSchemaChangesTo(120);

            // Nothing about the storage was edited: its version is still 1. Keyed on
            // the storage version, this migration would find a row already marked
            // applied and do nothing at all, on all three tenants at once.
            assertEquals("varchar(120)", columnType(SYSTEM, "amount"));
            assertEquals("varchar(120)", columnType(MID, "amount"));

            // The override is the whole reason a publish is N plans and not one plan
            // applied N times.
            assertEquals("varchar(10)", columnType(LEAF, "amount"));
        }

        @Test
        @Timeout(180)
        @DisplayName("the data already in the migrated tables survives")
        void dataSurvives() {

            givenTablesForEveryClient();
            whenSchemaChangesTo(120);

            // Widening a VARCHAR is classified as safe and applied with a plain
            // MODIFY, so this is the assertion that says the classification was right.
            assertEquals(1, scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE + "`.`" + TABLE
                    + "` WHERE `amount` = '10'"));
            assertEquals(1, scalar("SELECT COUNT(*) FROM `" + MID + "_" + APP_CODE + "`.`" + TABLE
                    + "` WHERE `amount` = '10'"));
        }

        @Test
        @Timeout(180)
        @DisplayName("the migration is journalled in the tenant that ran it, and not in the one that did not")
        void journalled() {

            givenTablesForEveryClient();
            whenSchemaChangesTo(120);

            for (String client : List.of(SYSTEM, MID))
                assertEquals(
                        1,
                        scalar("SELECT COUNT(*) FROM `" + client + "_" + APP_CODE
                                + "`.`storage_migration` WHERE `storage_name`='" + STORAGE_NAME
                                + "' AND `state`='APPLIED'"),
                        client + " should have one applied migration");

            // LEAF had nothing to do, and a journal row claiming otherwise would be a
            // record of DDL that never happened.
            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + LEAF + "_"
                            + APP_CODE + "' AND TABLE_NAME='storage_migration'"));
        }

        @Test
        @Timeout(180)
        @DisplayName("a field added to the schema becomes a column everywhere that inherits it")
        void addedField() {

            givenTablesForEveryClient();

            assertNull(columnType(SYSTEM, "currency"));

            // The schema stops being a bare string and becomes an object with two
            // fields, which is a shape change the storage knows nothing about.
            CoreSchema existing = asClient(coreSchemaService.read(MONEY, APP_CODE, SYSTEM), SYSTEM)
                    .getObject();
            CoreSchema edit = new CoreSchema(existing);
            edit.setDefinition(new HashMap<>(Map.of(
                    "namespace",
                    "App",
                    "name",
                    "Money",
                    "type",
                    "OBJECT",
                    "properties",
                    new HashMap<>(Map.of("value", new HashMap<>(Map.of("type", "DOUBLE")))))));
            asClient(coreSchemaService.update(edit), SYSTEM);

            // amount is now a nested object, so it becomes a JSON column rather than
            // being flattened into amount_value. Section 5.3 of the plan is why.
            assertEquals("json", columnType(SYSTEM, "amount"));
            assertEquals("json", columnType(MID, "amount"));
            assertEquals("varchar(10)", columnType(LEAF, "amount"), "the pinned override still wins");
        }

        @Test
        @Timeout(180)
        @DisplayName("a tenant with no table yet is not invented by the migration")
        void absentTenantIsNotCreated() {

            // Only MID has ever been written to, so only MID has a schema.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("amount", "10");
            row.put("note", "seed");
            asClient(
                    appDataService.create(APP_CODE, MID, STORAGE_NAME, new DataObject().setData(row), false, null),
                    MID);

            whenSchemaChangesTo(120);

            assertEquals("varchar(120)", columnType(MID, "amount"));

            // Tenants are discovered from the schemas that hold the table. Creating
            // one here would provision a client that has never used the app.
            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + LEAF + "_"
                            + APP_CODE + "'"));
        }

        @Test
        @Timeout(180)
        @DisplayName("editing the schema twice lands both changes")
        void successiveEdits() {

            givenTablesForEveryClient();

            whenSchemaChangesTo(120);
            whenSchemaChangesTo(200);

            // Two shapes, two journal rows. A second edit at the same storage version
            // is exactly the case that the old version-keyed journal would have
            // swallowed.
            assertEquals("varchar(200)", columnType(SYSTEM, "amount"));
            assertEquals(
                    2,
                    scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE
                            + "`.`storage_migration` WHERE `storage_name`='" + STORAGE_NAME + "'"));
        }
    }

    @Nested
    @DisplayName("when the storage itself changes")
    class StorageEdited {

        @Test
        @Timeout(180)
        @DisplayName("a new field on the base storage reaches every client's table")
        void baseEditReachesEveryone() {

            givenTablesForEveryClient();

            Storage system = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM), SYSTEM)
                    .getObject();

            Map<String, Object> props = new HashMap<>((Map<String, Object>) system.getSchema().get("properties"));
            props.put("ref1", new HashMap<>(Map.of("type", "STRING", "maxLength", 12)));

            Storage edit = new Storage(system);
            edit.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", props)));

            asClient(storageService.update(edit), SYSTEM);

            for (String client : List.of(SYSTEM, MID, LEAF))
                assertEquals("varchar(12)", columnType(client, "ref1"), client + " should have the new column");
        }

        @Test
        @Timeout(180)
        @DisplayName("the leaf's own override is still its own after a base edit")
        void overrideSurvivesBaseEdit() {

            givenTablesForEveryClient();

            Storage system = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM), SYSTEM)
                    .getObject();

            Map<String, Object> props = new HashMap<>((Map<String, Object>) system.getSchema().get("properties"));
            props.put("note", new HashMap<>(Map.of("type", "STRING", "maxLength", 90)));

            Storage edit = new Storage(system);
            edit.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", props)));

            asClient(storageService.update(edit), SYSTEM);

            assertTrue(
                    columnType(LEAF, "note").startsWith("varchar(90"),
                    "the leaf inherits note, so it widens with the base");
            assertEquals("varchar(10)", columnType(LEAF, "amount"), "but what it pinned stays pinned");
        }
    }
}
