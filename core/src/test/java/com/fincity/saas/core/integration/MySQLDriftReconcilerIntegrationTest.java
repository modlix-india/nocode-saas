package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLDrift;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The drift reconciler, driven through the service rather than in isolation.
 *
 * {@code MySQLDrift} had unit and integration tests from the day it was written and
 * was never called by anything: the classification was right and unreachable. So
 * these tests deliberately go in at {@code AppDataService}, through tenant
 * discovery, per-tenant definition resolution and the planners, because that wiring
 * is the part that was missing and therefore the part worth pinning.
 *
 * Every divergence here is created by going round the platform with raw DDL, which
 * is exactly how it happens in life: a hand-made index, an interrupted migration, a
 * table built from a shape the definition has since moved on from.
 */
@DisplayName("Drift, found and repaired through the service")
class MySQLDriftReconcilerIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String BOOKS = "books";
    private static final String TABLE = "testapp_books";
    private static final String TENANT = SYSTEM + "_" + APP_CODE;

    private static DSLContext ctx;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {
        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();
        this.dropTenants();
        this.givenConnection();
        this.givenStorage();
        this.writeBook("Dune");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static List<String> columnNames() {
        return Flux.from(ctx.resultQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = '" + TABLE + "'"
                        + " ORDER BY COLUMN_NAME"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();
    }

    private static List<String> indexNames() {
        return Flux.from(ctx.resultQuery("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = '" + TENANT + "' AND TABLE_NAME = '" + TABLE + "'"
                        + " ORDER BY INDEX_NAME"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorage() {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Map<String, Object> properties = new HashMap<>();
        properties.put("title", new HashMap<>(Map.of("type", "STRING", "maxLength", 120)));
        properties.put("isbn", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);

        Storage books = new Storage();
        books.setName(BOOKS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        books.setUniqueName(TABLE);
        books.setSchema(schema);
        this.insertRaw(books);
    }

    private void givenConnection() {
        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(
                        new HashMap<>(Map.of("url", mysqlUrl(), "username", "root", "password", "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("00000000000000drift0001");
        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private void writeBook(String title) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("isbn", "1");
        this.asClient(this.appDataService.create(APP_CODE, SYSTEM, BOOKS, new DataObject().setData(row), false, null));
    }

    /**
     * Every schema of this app, not just SYSTEM's.
     *
     * Drift reports one entry per tenant schema it finds, and the container is shared
     * by every test class in the run. MySQLTenantMigrationSafetyIntegrationTest and
     * MySQLSchemaChangeIntegrationTest leave their own client's schema behind, so
     * dropping only {@link #TENANT} passed alone and failed in CI with two reports
     * where one was expected. The draft siblings go too: the missing-table tests
     * create one.
     */
    private void dropTenants() {
        List<String> existing = Flux.from(ctx.resultQuery(
                        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE '%\\_"
                                + APP_CODE + "' OR SCHEMA_NAME LIKE '%\\_" + APP_CODE + "_draft'"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();

        for (String db : existing) exec("DROP DATABASE IF EXISTS `" + db + "`");
    }

    private List<MySQLDrift.Report> reports() {
        return this.asClient(this.appDataService.drift(APP_CODE, SYSTEM, BOOKS));
    }

    private MySQLDrift.Report report() {
        List<MySQLDrift.Report> reports = reports();
        assertEquals(1, reports.size(), "one schema holds this app, so there is exactly one report");
        return reports.getFirst();
    }

    private MySQLDrift.DriftRepair repair(boolean approved) {
        List<MySQLDrift.DriftRepair> out =
                this.asClient(this.appDataService.repairDrift(APP_CODE, SYSTEM, BOOKS, approved));
        assertEquals(1, out.size());
        return out.getFirst();
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("reporting")
    class Reporting {

        @Test
        @Timeout(300)
        @DisplayName("a table the platform just built reports clean")
        void cleanAfterCreate() {
            // If this is ever false the reconciler is unusable: it would report drift
            // on every table in the fleet and nobody could pick the real ones out.
            MySQLDrift.Report r = report();

            assertTrue(r.clean(), r.summary() + " / columns " + r.columns());
            assertFalse(r.needsApproval());
        }

        @Test
        @Timeout(300)
        @DisplayName("a column removed behind the platform's back is found")
        void missingColumnFound() {
            exec("ALTER TABLE `" + TENANT + "`.`" + TABLE + "` DROP COLUMN `isbn`");

            MySQLDrift.Report r = report();

            assertFalse(r.clean());
            assertEquals(1, r.columns().size());
            assertEquals("isbn", r.columns().getFirst().column());
            // Putting a column back loses nothing, so it must not need asking for.
            assertFalse(r.needsApproval(), "adding a missing column is additive");
        }

        @Test
        @Timeout(300)
        @DisplayName("a column the definition does not have needs approval to remove")
        void extraColumnNeedsApproval() {
            exec("ALTER TABLE `" + TENANT + "`.`" + TABLE + "` ADD COLUMN `rogue` VARCHAR(10)");

            MySQLDrift.Report r = report();

            assertFalse(r.clean());
            assertTrue(r.needsApproval());
            assertFalse(r.dangerous().isEmpty(), "dropping a column that may hold data is dangerous");
        }
    }

    @Nested
    @DisplayName("repairing")
    class Repairing {

        @Test
        @Timeout(300)
        @DisplayName("an unapproved repair restores a missing column")
        void unapprovedRepairAdds() {
            exec("ALTER TABLE `" + TENANT + "`.`" + TABLE + "` DROP COLUMN `isbn`");
            assertFalse(columnNames().contains("isbn"));

            MySQLDrift.DriftRepair out = repair(false);

            assertEquals(1, out.applied().size(), "the ADD should have run: " + out);
            assertTrue(out.failed().isEmpty());
            assertTrue(columnNames().contains("isbn"));
            assertTrue(report().clean(), "and the table is back in sync");
        }

        @Test
        @Timeout(300)
        @DisplayName("an unapproved repair withholds the drop and says which statement it withheld")
        void unapprovedRepairWithholds() {
            // The point of withheld: a count would say "something was skipped", and
            // the person deciding whether to approve needs to see WHAT.
            exec("ALTER TABLE `" + TENANT + "`.`" + TABLE + "` ADD COLUMN `rogue` VARCHAR(10)");

            MySQLDrift.DriftRepair out = repair(false);

            assertTrue(out.applied().isEmpty());
            assertEquals(1, out.withheld().size());
            assertTrue(out.withheld().getFirst().contains("DROP COLUMN `rogue`"), out.withheld().toString());
            assertFalse(out.complete());
            assertTrue(columnNames().contains("rogue"), "and the column is still there");
        }

        @Test
        @Timeout(300)
        @DisplayName("an approved repair drops it")
        void approvedRepairDrops() {
            exec("ALTER TABLE `" + TENANT + "`.`" + TABLE + "` ADD COLUMN `rogue` VARCHAR(10)");

            MySQLDrift.DriftRepair out = repair(true);

            assertTrue(out.withheld().isEmpty());
            assertTrue(out.failed().isEmpty(), out.failed().toString());
            assertFalse(columnNames().contains("rogue"));
            assertTrue(report().clean());
        }

        /**
         * Indexes are the half most likely to drift in practice, because adding one by
         * hand to chase a slow page is a thing people do and nothing records it.
         */
        @Test
        @Timeout(300)
        @DisplayName("an index added by hand is reported, and removing it needs approval")
        void handMadeIndex() {
            exec("CREATE INDEX `by_hand` ON `" + TENANT + "`.`" + TABLE + "` (`title`)");
            assertTrue(indexNames().contains("by_hand"));

            MySQLDrift.Report r = report();

            assertFalse(r.clean());
            assertFalse(r.drops().isEmpty(), "the stale index is a drop: " + r.indexStatements());
            assertTrue(r.needsApproval());

            assertTrue(repair(false).applied().isEmpty());
            assertTrue(indexNames().contains("by_hand"), "an unapproved repair leaves it alone");

            repair(true);
            assertFalse(indexNames().contains("by_hand"));
        }
    }

    @Nested
    @DisplayName("the tenant that has no table at all")
    class MissingTable {

        /**
         * The case the reconciler exists for and the one it could not see. Tenants
         * used to be discovered from the tables that EXIST, so a schema that missed
         * this table - unreachable when the storage changed, or provisioned before it
         * existed - reported nothing and looked fine.
         */
        @Test
        @Timeout(300)
        @DisplayName("a schema missing the table is reported, not skipped")
        void missingTableIsFound() {
            exec("CREATE DATABASE IF NOT EXISTS `" + TENANT + "_draft`");
            exec("CREATE TABLE IF NOT EXISTS `" + TENANT + "_draft`.`something_else` (`_id` CHAR(26) PRIMARY KEY)");

            List<MySQLDrift.Report> all = reports();

            MySQLDrift.Report missing = all.stream()
                    .filter(r -> r.db().endsWith("_draft"))
                    .findFirst()
                    .orElse(null);

            assertNotNull(missing, "the draft schema should be reported: " + all);
            assertFalse(missing.tableExists());
            assertFalse(missing.clean());
            assertTrue(missing.needsApproval(), "a table that is not there is not something to fix unasked");
            assertTrue(missing.summary().contains("MISSING"), missing.summary());
        }

        /**
         * And it must not be quietly reported as a successful repair. Nothing here
         * creates a table - that needs the whole definition, not a diff against
         * nothing - so the honest answer is an incomplete run.
         */
        @Test
        @Timeout(300)
        @DisplayName("repairing a missing table reports incomplete rather than done")
        void missingTableIsNotSilentlyComplete() {
            exec("CREATE DATABASE IF NOT EXISTS `" + TENANT + "_draft`");
            exec("CREATE TABLE IF NOT EXISTS `" + TENANT + "_draft`.`something_else` (`_id` CHAR(26) PRIMARY KEY)");

            MySQLDrift.DriftRepair draft =
                    this.asClient(appDataService.repairDrift(APP_CODE, SYSTEM, BOOKS, true)).stream()
                            .filter(r -> r.db().endsWith("_draft"))
                            .findFirst()
                            .orElseThrow();

            assertTrue(draft.tableMissing());
            assertFalse(draft.complete(), "a tenant with no table is not a clean run");
            assertTrue(draft.applied().isEmpty());
        }

        private <T> T asClient(Mono<T> mono) {
            return MySQLDriftReconcilerIntegrationTest.this.asClient(mono);
        }
    }

    @Nested
    @DisplayName("who may call it")
    class Security {

        /**
         * The gate is write access to the APP, not the storage's own authority.
         *
         * Two reasons it cannot be the storage's: a blank deleteAuth - which is most
         * storages - admits everybody, because hasAuthority(null, ..) is true by
         * design; and one call reaches every client's schema on the server, which a
         * per-client runtime authority does not describe.
         */
        @Test
        @Timeout(300)
        @DisplayName("the report is refused without write access to the app")
        void reportNeedsAppWriteAccess() {
            Mockito.when(feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            assertThrows(
                    Exception.class,
                    () -> MySQLDriftReconcilerIntegrationTest.this.asClient(
                            appDataService.drift(APP_CODE, SYSTEM, BOOKS)));
        }

        @Test
        @Timeout(300)
        @DisplayName("the repair is refused without write access to the app")
        void repairNeedsAppWriteAccess() {
            Mockito.when(feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            assertThrows(
                    Exception.class,
                    () -> MySQLDriftReconcilerIntegrationTest.this.asClient(
                            appDataService.repairDrift(APP_CODE, SYSTEM, BOOKS, true)));
        }

        /**
         * The storage's own authority must not buy a way in. A caller holding every
         * app-level role but no write access to the application is a user of the app,
         * and a user of the app has no business issuing DDL across its tenants.
         */
        @Test
        @Timeout(300)
        @DisplayName("holding the storage authorities is not a substitute")
        void storageAuthorityIsNotEnough() {
            Mockito.when(feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            ContextAuthentication ca =
                    MySQLDriftReconcilerIntegrationTest.this.authFor(SYSTEM, allAuthoritiesFor("Storage"));

            assertThrows(
                    Exception.class,
                    () -> appDataService
                            .repairDrift(APP_CODE, SYSTEM, BOOKS, true)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
                            .block());
        }
    }
}
