package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.FanOutReport;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The measured worst case, run for real: 71 tenant schemas for one storage, five
 * client levels deep, with overrides at three different depths.
 *
 * 71 is not an arbitrary number. It is what `cxapp` actually has, and it is the
 * reason this whole design is shaped the way it is: a publish is up to 71
 * independent, non-transactional migrations, any subset of which can fail. Three
 * tenants prove the mechanism; they do not prove it survives the width, and they
 * certainly do not prove that an override four levels up still reaches the leaves
 * underneath it.
 *
 * The tree, 71 clients in all:
 *
 * <pre>
 *   SYSTEM
 *     M0 .. M4                      five mid-level clients
 *       M0L0 .. M4L12               63 leaves spread across them
 *         M0L0C0                    and one branch carried deeper
 *           M0L0C1                  five levels from the root
 * </pre>
 *
 * Overrides are placed where they are most awkward rather than where they are
 * easiest: one on a mid-level client whose whole subtree must follow it, one on a
 * single leaf, and one at the very bottom of the deep branch.
 */
@DisplayName("One storage, 71 tenants, five client levels")
class MySQLWideFanOutIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String STORAGE_NAME = "ledger";
    private static final String TABLE = "testapp_wide_ledger";
    private static final String AMOUNT = "App.Amount";

    /** A mid-level client that pins its own width. Its whole subtree inherits that. */
    private static final String OVERRIDING_MID = "M2";

    /** One ordinary leaf that pins its own, under a mid that does not. */
    private static final String OVERRIDING_LEAF = "M1L5";

    /** The bottom of the deep branch. */
    private static final String DEEP_MID = "M0L0";
    private static final String DEEP_CHILD = "M0L0C0";
    private static final String DEEP_LEAF = "M0L0C1";

    /** Every client, and every client's base-first chain, built once. */
    private static final List<String> CLIENTS = new ArrayList<>();
    private static final Map<String, List<String>> CHAINS = new HashMap<>();
    private static final Map<String, String> PARENT = new HashMap<>();

    static {
        CLIENTS.add(SYSTEM);
        CHAINS.put(SYSTEM, List.of(SYSTEM));

        int[] leavesPerMid = {13, 13, 13, 12, 12};

        for (int m = 0; m < 5; m++) {
            String mid = "M" + m;
            register(mid, SYSTEM);

            for (int l = 0; l < leavesPerMid[m]; l++) register(mid + "L" + l, mid);
        }

        register(DEEP_CHILD, DEEP_MID);
        register(DEEP_LEAF, DEEP_CHILD);
    }

    private static void register(String client, String parent) {
        PARENT.put(client, parent);
        List<String> chain = new ArrayList<>(CHAINS.get(parent));
        chain.add(client);
        CHAINS.put(client, List.copyOf(chain));
        CLIENTS.add(client);
    }

    @Autowired
    private StorageService storageService;

    @Autowired
    private CoreSchemaService coreSchemaService;

    @Autowired
    private AppDataService appDataService;

    private static DSLContext ctx;

    // ---------------------------------------------------------------- harness

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> Mono.just(CHAINS.getOrDefault(call.getArgument(2), List.of(SYSTEM))));

        ctx = mysql();

        this.dropTenants();
        this.givenSchema("STRING", 40);
        this.givenStorages();
        this.givenConnection();
        this.givenTenantTables();
    }

    private void dropTenants() {
        // One query to find them rather than 71 blind drops: a leftover schema from a
        // previous run would otherwise be migrated by this one and confuse the counts.
        List<String> existing = Flux.from(ctx.resultQuery(
                        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE '%\\_"
                                + APP_CODE + "' OR SCHEMA_NAME LIKE '%\\_" + APP_CODE + "_draft'"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();

        for (String db : existing) exec("DROP DATABASE IF EXISTS `" + db + "`");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static String db(String client) {
        return client + "_" + APP_CODE;
    }

    private static String columnType(String client) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + db(client) + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    /** Journal rows in a tenant, or 0 when it has never had a migration at all. */
    private static long journalRows(String client) {

        if (scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + db(client)
                        + "' AND TABLE_NAME='storage_migration'")
                == 0) return 0;

        return scalar("SELECT COUNT(*) FROM `" + db(client) + "`.`storage_migration`");
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenSchema(String type, Integer maxLength) {

        Map<String, Object> def = new HashMap<>();
        def.put("namespace", "App");
        def.put("name", "Amount");
        def.put("type", type);
        if (maxLength != null) def.put("maxLength", maxLength);

        CoreSchema schema = new CoreSchema();
        schema.setName(AMOUNT).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        schema.setDefinition(def);

        this.insertRaw(schema);
    }

    /**
     * One storage document per client, in the stored delta form.
     *
     * Only the base carries the full schema. Everyone else stores their difference
     * from their parent, which for most of them is nothing at all - and a client that
     * stores nothing must still be migrated, because it inherits the reference and
     * therefore inherits the change.
     */
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

        for (String client : CLIENTS) {
            if (SYSTEM.equals(client)) continue;

            Storage storage = new Storage();
            storage.setName(STORAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(client)
                    .setBaseClientCode(PARENT.get(client))
                    .setVersion(1);
            storage.setUniqueName(TABLE);
            storage.setSchema(pinnedWidth(client) == null
                    ? new HashMap<>()
                    : new HashMap<>(Map.of(
                            "properties",
                            new HashMap<>(Map.of(
                                    "amount",
                                    new HashMap<>(Map.of("type", "STRING", "maxLength", pinnedWidth(client))))))));
            this.insertRaw(storage);
        }
    }

    /** What this client pins for itself, or null when it simply inherits. */
    private static Integer pinnedWidth(String client) {
        if (OVERRIDING_MID.equals(client)) return 15;
        if (OVERRIDING_LEAF.equals(client)) return 7;
        if (DEEP_LEAF.equals(client)) return 5;
        return null;
    }

    /** True when this client resolves to a width of its own, pinned here or above. */
    private static boolean isPinned(String client) {
        for (String ancestor : CHAINS.get(client)) if (pinnedWidth(ancestor) != null) return true;
        return false;
    }

    private static List<String> inheritors() {
        return CLIENTS.stream().filter(c -> !isPinned(c)).toList();
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
        conn.setId("0000000000000widefanout1");

        this.insertRaw(conn);
    }

    /**
     * Every tenant starts at the base shape with one short row.
     *
     * Seeded directly rather than through the write path, which is covered elsewhere
     * and would make this test mostly a measurement of 71 inserts. Starting everyone
     * identical is deliberate: the first reconcile then has to pull the overriders
     * apart from the inheritors on its own, which is the property under test rather
     * than something the fixture arranged.
     */
    private void givenTenantTables() {
        int i = 0;
        for (String client : CLIENTS) {
            exec("CREATE DATABASE `" + db(client) + "`");
            exec("CREATE TABLE `" + db(client) + "`.`" + TABLE + "` (`_id` CHAR(26) NOT NULL,"
                    + " `amount` VARCHAR(40) NULL, PRIMARY KEY (`_id`))");
            exec("INSERT INTO `" + db(client) + "`.`" + TABLE + "` VALUES ('" + String.format("%026d", i++)
                    + "', '10')");
        }
    }

    /**
     * The draft surface for all 71, created only by the test that needs it.
     *
     * In @BeforeEach it would double the setup cost of every other test in the class
     * for no gain.
     */
    private void givenDraftTenantTables() {
        int i = 0;
        for (String client : CLIENTS) {
            exec("CREATE DATABASE `" + db(client) + "_draft`");
            exec("CREATE TABLE `" + db(client) + "_draft`.`" + TABLE + "` (`_id` CHAR(26) NOT NULL,"
                    + " `amount` VARCHAR(40) NULL, PRIMARY KEY (`_id`))");
            exec("INSERT INTO `" + db(client) + "_draft`.`" + TABLE + "` VALUES ('" + String.format("%026d", i++)
                    + "', '10')");
        }
    }

    private static String draftColumnType(String client) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + db(client) + "_draft' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    private <T> T asSystem(Mono<T> mono) {

        String[] storage = allAuthoritiesFor("Storage");
        String[] schema = allAuthoritiesFor("Schema");
        String[] both = new String[storage.length + schema.length];
        System.arraycopy(storage, 0, both, 0, storage.length);
        System.arraycopy(schema, 0, both, storage.length, schema.length);

        ContextAuthentication ca = this.authFor(SYSTEM, both);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private FanOutReport reconcile() {
        Storage storage = this.asSystem(this.storageService.read(STORAGE_NAME, APP_CODE, SYSTEM))
                .getObject();
        return this.asSystem(this.appDataService.reconcileStorageDdl(APP_CODE, SYSTEM, storage));
    }

    private void whenSchemaWidensTo(int maxLength) {

        CoreSchema existing = this.asSystem(this.coreSchemaService.read(AMOUNT, APP_CODE, SYSTEM))
                .getObject();

        CoreSchema edit = new CoreSchema(existing);
        edit.setDefinition(new HashMap<>(Map.of(
                "namespace", "App", "name", "Amount", "type", "STRING", "maxLength", maxLength)));

        this.asSystem(this.coreSchemaService.update(edit));
    }

    private void whenSchemaBecomesNumeric() {

        CoreSchema existing = this.asSystem(this.coreSchemaService.read(AMOUNT, APP_CODE, SYSTEM))
                .getObject();

        CoreSchema edit = new CoreSchema(existing);
        edit.setDefinition(new HashMap<>(Map.of("namespace", "App", "name", "Amount", "type", "DOUBLE")));

        this.asSystem(this.coreSchemaService.update(edit));
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Timeout(300)
    @DisplayName("the tree is what it claims to be")
    void treeShape() {
        assertEquals(71, CLIENTS.size(), "71 is the measured worst case, not a round number");
        assertEquals(5, CHAINS.get(DEEP_LEAF).size(), "SYSTEM -> M0 -> M0L0 -> M0L0C0 -> M0L0C1");
        assertEquals(List.of(SYSTEM, "M0", DEEP_MID, DEEP_CHILD, DEEP_LEAF), CHAINS.get(DEEP_LEAF));

        // M2 and its 13 leaves is 14, plus the one pinned leaf, plus the deepest
        // client: 16 tenants that resolve a width of their own, 55 that inherit.
        assertEquals(16, CLIENTS.stream().filter(MySQLWideFanOutIntegrationTest::isPinned).count());
        assertEquals(55, inheritors().size());
    }

    @Test
    @Timeout(300)
    @DisplayName("one publish resolves 71 different definitions, and gives the overriders their own tables")
    void eachTenantGetsItsOwnShape() {

        FanOutReport report = reconcile();

        assertNotNull(report);
        assertTrue(report.allClean(), report.summary());

        // A publish is N plans, not one plan applied N times. The overriders diverge
        // from the base here without anything in the fixture having arranged it.
        assertEquals("varchar(15)", columnType(OVERRIDING_MID));
        assertEquals("varchar(7)", columnType(OVERRIDING_LEAF));
        assertEquals("varchar(5)", columnType(DEEP_LEAF));

        // The mid's override has to reach its whole subtree, four levels of it in one
        // case. A leaf resolving against its own chain rather than the publisher's is
        // the only way this comes out right.
        assertEquals("varchar(15)", columnType(OVERRIDING_MID + "L0"));
        assertEquals("varchar(15)", columnType(OVERRIDING_MID + "L12"));

        // And the deep branch above the pin still follows the base.
        assertEquals("varchar(40)", columnType(DEEP_MID));
        assertEquals("varchar(40)", columnType(DEEP_CHILD));

        for (String client : inheritors()) assertEquals("varchar(40)", columnType(client), client);
    }

    @Test
    @Timeout(300)
    @DisplayName("a schema edit reaches all 55 inheritors and leaves the 16 overriders alone")
    void schemaEditAcrossSeventyOne() {

        reconcile();

        // The pinned leaf already has a journal row: the first reconcile is what gave
        // it its own width. What must not happen is a SECOND one.
        long pinnedRowsBefore = journalRows(OVERRIDING_LEAF);
        assertEquals(1, pinnedRowsBefore);

        whenSchemaWidensTo(120);

        List<String> inheritors = inheritors();
        assertEquals(55, inheritors.size());

        for (String client : inheritors) assertEquals("varchar(120)", columnType(client), client);

        // Sixteen tenants resolve a width of their own, whether pinned on themselves
        // or inherited from a mid that pinned. A fan-out that moved them would be
        // overwriting a client's deliberate choice with somebody else's edit.
        assertEquals("varchar(15)", columnType(OVERRIDING_MID));
        assertEquals("varchar(15)", columnType(OVERRIDING_MID + "L7"));
        assertEquals("varchar(7)", columnType(OVERRIDING_LEAF));
        assertEquals("varchar(5)", columnType(DEEP_LEAF));

        // Journalled per tenant, in the tenant, and only where work happened. M3L3 was
        // already at the base width, so the first reconcile left it alone and this
        // edit is its first migration.
        assertEquals(1, journalRows("M3L3"));
        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + db("M3L3") + "`.`storage_migration` WHERE `state`='APPLIED'"));

        assertEquals(
                pinnedRowsBefore,
                journalRows(OVERRIDING_LEAF),
                "a tenant the edit does not affect must not gain a row claiming DDL that never ran");
    }

    @Test
    @Timeout(600)
    @DisplayName("one publish across 142 schemas, both surfaces, with the overrides intact")
    void bothSurfacesAtWidth() {

        givenDraftTenantTables();

        assertEquals(
                142,
                scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_NAME='" + TABLE + "'"),
                "71 clients on two surfaces");

        long start = System.currentTimeMillis();
        FanOutReport first = reconcile();
        whenSchemaWidensTo(120);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(first);

        // A derived client with no draft of its own resolves the live definition on
        // the draft surface too, so the published change reaches both tables.
        for (String client : inheritors()) {
            assertEquals("varchar(120)", columnType(client), client + " live");
            assertEquals("varchar(120)", draftColumnType(client), client + " draft");
        }

        // And the overrides hold on the sandbox exactly as they do in production. A
        // draft surface that quietly ignored a client's pinned type would be a
        // sandbox that does not resemble the thing it is a sandbox for.
        assertEquals("varchar(15)", draftColumnType(OVERRIDING_MID));
        assertEquals("varchar(15)", draftColumnType(OVERRIDING_MID + "L9"));
        assertEquals("varchar(7)", draftColumnType(OVERRIDING_LEAF));
        assertEquals("varchar(5)", draftColumnType(DEEP_LEAF));

        // Not an assertion about speed, which would be a flaky test on shared CI.
        // It is here so the number is written down: a publish at the measured worst
        // case is seconds, not minutes, and if that ever stops being true the figure
        // to compare against is in the output.
        System.out.println("142-schema publish across 71 tenants took " + elapsed + "ms");
    }

    @Test
    @Timeout(300)
    @DisplayName("three bad tenants out of 71 block only themselves, and a re-run finishes the rest")
    void partialFailureAtWidth() {

        reconcile();

        // Three tenants at three different depths hold a value no number can be made
        // of: a mid, an ordinary leaf, and the bottom of the deep branch.
        List<String> dirty = List.of("M3", "M4L2", DEEP_CHILD);
        for (String client : dirty)
            exec("UPDATE `" + db(client) + "`.`" + TABLE + "` SET `amount` = 'not a number'");

        whenSchemaBecomesNumeric();

        FanOutReport report = reconcile();
        assertNotNull(report);

        // Every one of them named, not just the first. Learning about one bad tenant,
        // fixing it, then learning about the next is how a 71-way publish becomes a
        // week of work.
        assertFalse(report.allClean(), report.summary());
        for (String client : dirty) assertEquals("varchar(40)", columnType(client), client + " must be untouched");

        // M3's own leaves are separate tenants with their own clean data, so they
        // migrate even though their parent could not.
        assertEquals("double", columnType("M3L0"));
        assertEquals("double", columnType("M3L11"));

        // The deep branch above the blocked child migrated; the child did not.
        assertEquals("double", columnType(DEEP_MID));

        for (String client : dirty)
            exec("UPDATE `" + db(client) + "`.`" + TABLE + "` SET `amount` = '99'");

        FanOutReport second = reconcile();

        assertTrue(second.allClean(), second.summary());
        for (String client : dirty) assertEquals("double", columnType(client), client + " should have caught up");

        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + db("M3") + "`.`" + TABLE + "` WHERE `amount` = 99"),
                "the fixed value converted rather than being dropped");
    }
}
