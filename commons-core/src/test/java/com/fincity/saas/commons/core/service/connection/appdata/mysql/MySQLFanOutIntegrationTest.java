package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * A storage change spread across several tenant schemas, against a real MySQL.
 *
 * The single-tenant path is covered elsewhere. What matters here is what happens when
 * one tenant's data blocks and the others are fine.
 */
@Testcontainers
class MySQLFanOutIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String APP = "fanapp";
    private static final String TABLE = "orders";
    private static final List<String> TENANTS = List.of("CLA_" + APP, "CLB_" + APP, "CLC_" + APP);

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private static String columnType(String db, String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + db + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    @BeforeEach
    void freshTenants() {
        int i = 0;
        for (String db : TENANTS) {
            exec("DROP DATABASE IF EXISTS `" + db + "`");
            exec("CREATE DATABASE `" + db + "`");
            exec("CREATE TABLE `" + db + "`.`" + TABLE + "` (`_id` CHAR(26) NOT NULL,"
                    + " `amount` VARCHAR(40) NULL, PRIMARY KEY (`_id`))");
            exec("INSERT INTO `" + db + "`.`" + TABLE + "` VALUES ('" + String.format("%026d", i++) + "', '10')");
        }
    }

    /** Every tenant wants amount as a DOUBLE: the base definition changed. */
    private static List<MySQLColumn> desired(String db) {
        return List.of(new MySQLColumn("amount", "DOUBLE", true));
    }

    private static List<TenantPlan> plans() {
        return plans(MySQLFanOutIntegrationTest::desired);
    }

    private static List<TenantPlan> plans(java.util.function.Function<String, List<MySQLColumn>> desiredFor) {
        return MySQLFanOut.plan(ctx, TENANTS, TABLE, 9, desiredFor).block();
    }

    private static FanOutReport apply() {
        return apply(MySQLFanOutIntegrationTest::desired);
    }

    private static FanOutReport apply(java.util.function.Function<String, List<MySQLColumn>> desiredFor) {
        return MySQLFanOut.apply(ctx, plans(desiredFor), "orders", TABLE, 8, 9, "LIVE", "kiran")
                .block();
    }

    @Test
    @DisplayName("a referenced schema changing re-migrates every tenant at the same version")
    void schemaChangeAtSameVersion() {
        assertTrue(apply().allClean());

        // The storage was not edited. A schema it references gained a field, so every
        // tenant's table is now a column short at version 9 - the same version that is
        // already journalled as applied. Keyed on the version this would be skipped
        // everywhere, silently, for all three tenants at once.
        FanOutReport report = apply(db -> List.of(
                new MySQLColumn("amount", "DOUBLE", true), new MySQLColumn("currency", "VARCHAR(3)", true)));

        assertTrue(report.allClean(), report.summary());
        assertEquals(3, report.applied(), report.summary());
        for (String db : TENANTS) assertEquals("varchar(3)", columnType(db, "currency"));
    }

    @Test
    @DisplayName("a tenant whose own override already matches is left alone")
    void overrideUnaffected() {
        // CLB pinned amount as a DOUBLE of its own, so the base change is a no-op for
        // it. That is not the same as nothing happening: it is the overridable
        // definition working, and a fan-out that reported it as migrated would be
        // claiming DDL it never issued.
        exec("ALTER TABLE `CLB_" + APP + "`.`" + TABLE + "` MODIFY COLUMN `amount` DOUBLE NULL");

        FanOutReport report = apply();

        assertTrue(report.noOp().contains("CLB_" + APP), report.summary());
        assertEquals(2, report.applied(), report.summary());
    }

    @Test
    @DisplayName("how far the publish got can be read back per tenant")
    void progressIsReadable() {
        // One tenant cannot convert, so the publish lands on two of three.
        exec("UPDATE `CLB_" + APP + "`.`" + TABLE + "` SET `amount` = 'nope'");
        apply();

        Map<String, TenantProgress> byDb = MySQLFanOut.status(ctx, TENANTS, "orders").block().stream()
                .collect(java.util.stream.Collectors.toMap(TenantProgress::database, p -> p));

        assertTrue(byDb.get("CLA_" + APP).isDone());
        assertTrue(byDb.get("CLC_" + APP).isDone());

        // The blocked tenant was never attempted, so it has no row - which is the
        // point of pre-flighting. A FAILED row here would then block the storage until
        // someone cleared it, for a tenant nothing was ever done to.
        assertTrue(byDb.get("CLB_" + APP).isUntouched(), byDb.get("CLB_" + APP).describe());
    }

    @Test
    @DisplayName("re-running the publish carries the stragglers forward and leaves the rest alone")
    void rerunResumes() {
        exec("UPDATE `CLB_" + APP + "`.`" + TABLE + "` SET `amount` = 'nope'");

        FanOutReport first = apply();
        assertEquals(2, first.applied());
        assertEquals(List.of("CLB_" + APP), first.blocked());

        // Someone fixes the one tenant that was wrong. Re-running the whole publish is
        // all they should have to do: deciding which of 71 schemas to re-run by hand is
        // not a recovery procedure anyone actually follows.
        exec("UPDATE `CLB_" + APP + "`.`" + TABLE + "` SET `amount` = '11'");

        FanOutReport second = apply();

        assertTrue(second.allClean(), second.summary());
        assertEquals("double", columnType("CLB_" + APP, "amount"));

        // And the two that were already done are recognised as done rather than redone,
        // by the diff rather than the journal: their table is already the right shape,
        // so there is no plan to run. The journal guard behind it only has to catch the
        // case where the shape alone cannot tell, which is a finished expand-contract.
        assertEquals(1, second.applied(), second.summary());
        assertEquals(2, second.noOp().size(), second.summary());
        assertTrue(second.noOp().contains("CLA_" + APP), second.summary());
    }

    @Test
    @DisplayName("status before anything runs says nobody has been touched")
    void progressBeforeAnything() {
        List<TenantProgress> progress =
                MySQLFanOut.status(ctx, TENANTS, "orders").block();

        assertEquals(3, progress.size());
        assertTrue(progress.stream().allMatch(TenantProgress::isUntouched));
        assertTrue(progress.get(0).describe().contains("not attempted"));
    }

    @Test
    @DisplayName("every tenant gets its own plan")
    void onePlanPerTenant() {
        List<TenantPlan> plans = plans();
        assertEquals(3, plans.size());
        assertTrue(plans.stream().noneMatch(TenantPlan::isNoOp));
        assertTrue(plans.stream().allMatch(TenantPlan::needsCheck), "a retype must be pre-flighted");
    }

    @Test
    @DisplayName("all tenants clean: every one migrates")
    void allClean() {
        FanOutReport report = apply();

        assertTrue(report.allClean(), report.summary());
        assertEquals(3, report.applied());
        for (String db : TENANTS) assertEquals("double", columnType(db, "amount"));
    }

    @Test
    @DisplayName("one tenant's dirty data blocks only that tenant")
    void oneBlockedTheOthersProceed() {
        exec("UPDATE `CLB_" + APP + "`.`" + TABLE + "` SET `amount` = 'junk'");

        FanOutReport report = apply();

        assertFalse(report.allClean());
        assertEquals(List.of("CLB_" + APP), report.blocked());
        assertEquals(2, report.applied(), report.summary());

        assertEquals("double", columnType("CLA_" + APP, "amount"));
        assertEquals("double", columnType("CLC_" + APP, "amount"));
        assertEquals("varchar(40)", columnType("CLB_" + APP, "amount"), "the blocked tenant is untouched");
    }

    @Test
    @DisplayName("a blocked tenant is never attempted, so it leaves no failed journal row behind")
    void blockedTenantIsNotAttempted() {
        exec("UPDATE `CLB_" + APP + "`.`" + TABLE + "` SET `amount` = 'junk'");

        apply();

        // Attempting it would have stopped at the same check a moment later, having
        // written a FAILED row that then blocks the storage until someone clears it.
        assertEquals(
                0,
                scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='CLB_" + APP
                        + "' AND TABLE_NAME='" + MySQLMigrationJournal.TABLE + "'"),
                "no journal table should even have been created for a tenant that never ran");
    }

    @Test
    @DisplayName("the pre-flight itself alters nothing, even when every tenant is clean")
    void preflightIsReadOnly() {
        Map<String, Long> blocked = MySQLFanOut.preflight(ctx, plans()).block();

        assertTrue(blocked.isEmpty());
        for (String db : TENANTS)
            assertEquals("varchar(40)", columnType(db, "amount"), "pre-flight must not touch the table");
    }

    @Test
    @DisplayName("the pre-flight names every tenant that would fail, not just the first")
    void preflightReportsAllFailures() {
        exec("UPDATE `CLA_" + APP + "`.`" + TABLE + "` SET `amount` = 'junk'");
        exec("UPDATE `CLC_" + APP + "`.`" + TABLE + "` SET `amount` = 'rubbish'");

        Map<String, Long> blocked = MySQLFanOut.preflight(ctx, plans()).block();

        // Learning about all of them before migrating anything is the entire point.
        assertEquals(2, blocked.size(), blocked.toString());
        assertTrue(blocked.containsKey("CLA_" + APP));
        assertTrue(blocked.containsKey("CLC_" + APP));
    }

    @Test
    @DisplayName("a tenant already at the right shape is a no-op, not a migration")
    void alreadyCurrentIsANoOp() {
        apply();

        FanOutReport second = MySQLFanOut.apply(ctx, plans(), "orders", TABLE, 9, 10, "LIVE", "kiran")
                .block();

        assertEquals(3, second.noOp().size(), second.summary());
        assertEquals(0, second.applied());
    }

    @Test
    @DisplayName("a tenant without the table yet is left for the ordinary lazy create")
    void missingTableIsNotAMigration() {
        exec("DROP TABLE `CLC_" + APP + "`.`" + TABLE + "`");

        List<TenantPlan> plans = plans();
        TenantPlan c = plans.stream()
                .filter(p -> p.database().equals("CLC_" + APP))
                .findFirst()
                .orElseThrow();

        assertTrue(c.isNoOp(), "a table that does not exist is created at the right shape, not migrated");
    }

    @Test
    @DisplayName("tenants are discovered from what exists, including draft schemas")
    void discoversTenantsIncludingDrafts() {
        exec("DROP DATABASE IF EXISTS `CLA_" + APP + "_draft`");
        exec("CREATE DATABASE `CLA_" + APP + "_draft`");
        exec("CREATE TABLE `CLA_" + APP + "_draft`.`" + TABLE + "` (`_id` CHAR(26) NOT NULL, PRIMARY KEY (`_id`))");

        List<String> found = MySQLTableInspector.tenantsWithTable(ctx, APP, TABLE).block();

        assertTrue(found.containsAll(TENANTS), found.toString());
        assertTrue(found.contains("CLA_" + APP + "_draft"), "the draft surface is a tenant too");

        exec("DROP DATABASE `CLA_" + APP + "_draft`");
    }
}
