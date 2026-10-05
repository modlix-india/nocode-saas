package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.fincity.saas.commons.core.service.CorePublishService;
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
 * The draft surface is a second set of tables, and it has to move on its own.
 *
 * The rebuild is hooked on cache eviction rather than on {@code update}, because
 * every mutating path already ends there and wiring it per method is how the next
 * path gets missed. These tests are what says that is actually true: a draft save,
 * a publish and a discard all change a definition without going anywhere near
 * {@code update}, and every one of them has to reach the right tables.
 *
 * Getting this wrong in either direction is bad in a specific way. A draft edit that
 * moves the live table alters production from a sandbox. A publish that does not move
 * the live table leaves the published definition describing a table that does not
 * match it.
 */
@DisplayName("Draft, publish and discard each reach the right tables")
class MySQLDraftPublishIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String MID = "LZCLA";
    private static final String LEAF = "LZACP1";

    private static final String STORAGE_NAME = "invoices";
    private static final String TABLE = "testapp_invoices";
    private static final String AMOUNT = "App.Amount";

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

    @Autowired
    private CorePublishService publishService;

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

        this.givenSchema(40);
        this.givenStorages();
        this.givenConnection();
        this.givenBothSurfaces();
    }

    // ---------------------------------------------------------------- harness

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static String live(String client) {
        return columnType(client + "_" + APP_CODE);
    }

    private static String draft(String client) {
        return columnType(client + "_" + APP_CODE + "_draft");
    }

    private static String columnType(String db) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private void givenSchema(int maxLength) {

        CoreSchema schema = new CoreSchema();
        schema.setName(AMOUNT).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        schema.setDefinition(new HashMap<>(
                Map.of("namespace", "App", "name", "Amount", "type", "STRING", "maxLength", maxLength)));

        this.insertRaw(schema);
    }

    private void givenStorages() {

        Storage base = new Storage();
        base.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        base.setUniqueName(TABLE);
        base.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of("amount", new HashMap<>(Map.of("ref", AMOUNT)))))));
        this.insertRaw(base);

        for (String[] pair : new String[][] {{MID, SYSTEM}, {LEAF, MID}}) {
            Storage derived = new Storage();
            derived.setName(STORAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(pair[0])
                    .setBaseClientCode(pair[1])
                    .setVersion(1);
            derived.setUniqueName(TABLE);
            derived.setSchema(new HashMap<>());
            this.insertRaw(derived);
        }
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
        conn.setId("0000000000000draftpub001");

        this.insertRaw(conn);
    }

    /** One write per client per surface, which is what makes six tables exist. */
    private void givenBothSurfaces() {
        for (String client : List.of(SYSTEM, MID, LEAF)) {
            this.asClient(this.write(client), client);
            this.asClient(this.onDraftSurface(this.write(client)), client);
        }
    }

    private Mono<Map<String, Object>> write(String client) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", "10");
        return this.appDataService.create(
                APP_CODE, client, STORAGE_NAME, new DataObject().setData(row), false, null);
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {

        String[] storage = allAuthoritiesFor("Storage");
        String[] schema = allAuthoritiesFor("Schema");
        String[] both = new String[storage.length + schema.length];
        System.arraycopy(storage, 0, both, 0, storage.length);
        System.arraycopy(schema, 0, both, storage.length, schema.length);

        ContextAuthentication ca = this.authFor(clientCode, both);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asSystem(Mono<T> mono) {
        return this.asClient(mono, SYSTEM);
    }

    private CoreSchema storedSchema() {
        return this.asSystem(this.coreSchemaService.read(AMOUNT, APP_CODE, SYSTEM)).getObject();
    }

    private static CoreSchema widened(CoreSchema from, int maxLength) {
        CoreSchema edit = new CoreSchema(from);
        edit.setDefinition(new HashMap<>(
                Map.of("namespace", "App", "name", "Amount", "type", "STRING", "maxLength", maxLength)));
        return edit;
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Timeout(240)
    @DisplayName("both surfaces start from the same definition")
    void baseline() {
        for (String client : List.of(SYSTEM, MID, LEAF)) {
            assertEquals("varchar(40)", live(client), client);
            assertEquals("varchar(40)", draft(client), client);
        }
    }

    @Test
    @Timeout(240)
    @DisplayName("a schema draft moves the drafting client's draft table and no live table at all")
    void draftSaveMovesOnlyDraftTables() {

        assertNotNull(asSystem(coreSchemaService.saveDraft(widened(storedSchema(), 120))));

        // The point of the draft surface. A sandbox edit that altered production
        // tables would make it the most dangerous feature in the product.
        assertEquals("varchar(120)", draft(SYSTEM), "the drafting client's sandbox should follow its draft");

        for (String client : List.of(SYSTEM, MID, LEAF))
            assertEquals("varchar(40)", live(client), client + " live must not move on a draft save");
    }

    @Test
    @Timeout(240)
    @DisplayName("a base client's unpublished draft does not reach a derived client's draft table")
    void ancestorDraftDoesNotLeakDownstream() {

        asSystem(coreSchemaService.saveDraft(widened(storedSchema(), 120)));

        // Deliberate, and the table has to honour it. `readDrafted` serves only the
        // requesting client's OWN draft, because the most-derived document in a chain
        // is usually an ancestor's and the base is usually SYSTEM: substituting its
        // draft would put SYSTEM's unpublished work in every tenant's sandbox. The
        // same reasoning has to hold one layer down, or the document says one thing
        // and the table another.
        assertEquals("varchar(40)", draft(MID), "MID has no draft of its own");
        assertEquals("varchar(40)", draft(LEAF), "nor does LEAF");
    }

    @Test
    @Timeout(240)
    @DisplayName("publishing the draft moves the live tables")
    void publishMovesLiveTables() {

        asSystem(coreSchemaService.saveDraft(widened(storedSchema(), 120)));

        Map<String, Object> result = asSystem(publishService.publishAll(APP_CODE, SYSTEM));
        assertEquals(1L, result.get("published"), "publishAll reported: " + result.get("results"));

        // publish goes nowhere near update(). It promotes the draft document and
        // drops the caches, which is exactly why the rebuild hangs off the eviction.
        for (String client : List.of(SYSTEM, MID, LEAF)) {
            assertEquals("varchar(120)", live(client), client + " live should have moved on publish");
            assertEquals("varchar(120)", draft(client), client);
        }
    }

    @Test
    @Timeout(240)
    @DisplayName("discarding the draft puts the draft tables back")
    void discardRestoresDraftTables() {

        asSystem(coreSchemaService.saveDraft(widened(storedSchema(), 120)));
        assertEquals("varchar(120)", draft(SYSTEM));

        asSystem(coreSchemaService.discardDraft(storedSchema().getId()));

        // With the draft gone the draft surface resolves the live definition again,
        // so the sandbox goes back to matching production rather than being left on
        // a shape nothing describes.
        for (String client : List.of(SYSTEM, MID, LEAF)) {
            assertEquals("varchar(40)", draft(client), client + " draft should be back");
            assertEquals("varchar(40)", live(client), client);
        }
    }

    @Test
    @Timeout(240)
    @DisplayName("a storage draft reaches the drafting client's draft table only")
    void storageDraftStaysWithItsClient() {

        Storage base = asSystem(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM)).getObject();

        Map<String, Object> props = new HashMap<>((Map<String, Object>) base.getSchema().get("properties"));
        props.put("memo", new HashMap<>(Map.of("type", "STRING", "maxLength", 25)));

        Storage edit = new Storage(base);
        edit.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", props)));

        asSystem(storageService.saveDraft(edit));

        assertEquals(
                "varchar(25)",
                columnTypeOf(SYSTEM + "_" + APP_CODE + "_draft", "memo"),
                "the drafting client's sandbox gets the new column");

        // Same rule as a schema draft: a derived client with no draft of its own
        // resolves the live definition, so its sandbox is unchanged.
        for (String client : List.of(MID, LEAF))
            assertEquals(
                    null,
                    columnTypeOf(client + "_" + APP_CODE + "_draft", "memo"),
                    client + " has no draft of its own");

        for (String client : List.of(SYSTEM, MID, LEAF))
            assertEquals(
                    null,
                    columnTypeOf(client + "_" + APP_CODE, "memo"),
                    client + " live must not have it yet");
    }

    @Test
    @Timeout(240)
    @DisplayName("a derived client drafting for itself moves only its own draft table")
    void derivedClientDraftsForItself() {

        Storage mid = asClient(storageService.read(STORAGE_NAME, APP_CODE, MID), MID).getObject();

        Map<String, Object> props = new HashMap<>((Map<String, Object>) mid.getSchema().get("properties"));
        props.put("midOnly", new HashMap<>(Map.of("type", "STRING", "maxLength", 18)));

        Storage edit = new Storage(mid);
        edit.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", props)));

        asClient(storageService.saveDraft(edit), MID);

        assertEquals("varchar(18)", columnTypeOf(MID + "_" + APP_CODE + "_draft", "midOnly"));

        // Not upward into the base's sandbox, and not downward into the leaf's.
        assertEquals(null, columnTypeOf(SYSTEM + "_" + APP_CODE + "_draft", "midOnly"));
        assertEquals(null, columnTypeOf(LEAF + "_" + APP_CODE + "_draft", "midOnly"));
        assertEquals(null, columnTypeOf(MID + "_" + APP_CODE, "midOnly"), "and not into its own live table");
    }

    @Test
    @Timeout(240)
    @DisplayName("data on both surfaces survives its own migration")
    void dataSurvivesOnBothSurfaces() {

        asSystem(coreSchemaService.saveDraft(widened(storedSchema(), 120)));
        asSystem(publishService.publishAll(APP_CODE, SYSTEM));

        assertTrue(rows(MID + "_" + APP_CODE) == 1, "live row");
        assertTrue(rows(MID + "_" + APP_CODE + "_draft") == 1, "draft row");
    }

    private static String columnTypeOf(String db, String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + db
                        + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private static long rows(String db) {
        Number n = Mono.from(ctx.resultQuery("SELECT COUNT(*) FROM `" + db + "`.`" + TABLE + "` WHERE `amount`='10'"))
                .map(r -> (Number) r.get(0))
                .block();
        return n == null ? 0L : n.longValue();
    }
}
