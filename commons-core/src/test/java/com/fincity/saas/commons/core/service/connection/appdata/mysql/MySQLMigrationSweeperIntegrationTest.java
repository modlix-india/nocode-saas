package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.SchemaChange.Kind;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * A migration that a restart interrupted, finished by the sweep.
 *
 * Resuming is already covered where a publish drives it. What only this can show is
 * recovery with no publish and no storage definition in sight: the sweep finds the
 * row, reads the plan out of it, and carries on. That is the deployment case, and
 * the reason the plan is stored rather than re-derived.
 */
@Testcontainers
class MySQLMigrationSweeperIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "sweepdata";
    private static final String TABLE = "ledger";
    private static final String STORAGE = "ledger";

    private static final String SHAPE =
            MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("amount", "DOUBLE", true)));

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("sweepdata");

        // Root, because one of these tests creates a second tenant schema to prove
        // the sweep discovers tenants rather than being handed a list.
        exec("CREATE DATABASE IF NOT EXISTS `" + DB + "`");
    }

    @BeforeEach
    void freshTable() {
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`");
        // The snapshot the planner takes before anything destructive. It survives the
        // table it was made from, which is the point, so the fixture clears it too.
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "__bak_7`");
        exec("CREATE TABLE `" + DB + "`.`" + TABLE + "` (`_id` CHAR(26) NOT NULL, `amount` VARCHAR(40) NULL,"
                + " PRIMARY KEY (`_id`))");
        exec("INSERT INTO `" + DB + "`.`" + TABLE + "` VALUES ('00000000000000000000000001', '42')");

        // Dropped rather than emptied. One test here deliberately builds an OLD
        // journal table to prove the upgrade; leaving it behind would hand the next
        // test a table missing columns it needs.
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "`");
        MySQLMigrationJournal.ensure(ctx, DB).block();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private static String str(String sql) {
        return Mono.from(ctx.resultQuery(sql)).map(r -> String.valueOf(r.get(0))).block();
    }

    private static List<MigrationStep> plan() {
        return MySQLMigrationPlanner.plan(
                DB, TABLE, 7, List.of(new SchemaChange(Kind.NARROWING, "amount", "VARCHAR(40)", "DOUBLE", "retype")));
    }

    private static String amountType() {
        return str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'");
    }

    /**
     * Run the plan up to and including the given phase, then abandon it exactly as a
     * killed process would: a RUNNING row pointing at the next step, with a claim
     * old enough to be stale.
     */
    private static String interruptAfter(MigrationStep.Phase phase, int staleSeconds) {

        List<MigrationStep> plan = plan();
        int stoppedAt = 0;

        for (int i = 0; i < plan.size(); i++) {
            MigrationStep s = plan.get(i);
            if (s.sql() != null) exec(s.sql());
            if (s.phase() == phase) {
                stoppedAt = i + 1;
                break;
            }
        }

        String id = "01SWEEP" + String.format("%019d", System.nanoTime() % 1_000_000_000L);

        exec(MySQLMigrationJournal.insert(
                DB, id, STORAGE, TABLE, 6, 7, SHAPE, "LIVE", plan.size(), "kiran",
                MigrationPlanCodec.write(plan), "dead-node"));

        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `state`='RUNNING', `statement_index`="
                + stoppedAt + ", `claimed_at` = NOW(3) - INTERVAL " + staleSeconds + " SECOND WHERE `id`='" + id
                + "'");

        return id;
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("an interrupted migration is found, claimed and finished, with no publish")
    void sweepFinishesIt() {

        interruptAfter(MigrationStep.Phase.EXPAND, 600);
        assertEquals("varchar(40)", amountType(), "left part way");

        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        // Nothing resolved a storage definition or touched Mongo to do this. The row
        // carried everything it needed.
        assertEquals(1, report.found(), report.summary());
        assertEquals(1, report.claimed(), report.summary());
        assertEquals(1, report.resumed(), report.summary());

        assertEquals("double", amountType());
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + TABLE + "` WHERE `amount` = 42"));
        assertEquals(
                "APPLIED",
                str("SELECT `state` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "`"));
    }

    @Test
    @DisplayName("the mid-contract window, which is the one that actually breaks a tenant")
    void sweepRecoversMidContract() {

        String id = interruptAfter(MigrationStep.Phase.CONTRACT, 600);

        // The original column is gone and the temporary one is not yet renamed, so
        // reads and writes of `amount` fail. This is the state a deploy can land in.
        assertEquals(
                0,
                scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='amount'"));

        List<InterruptedMigration> rows = MySQLMigrationSweeper.interrupted(ctx, DB, 60).block();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isMidContract(), rows.get(0).describe());

        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        assertEquals(1, report.resumed(), report.summary());
        assertEquals("double", amountType());
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + TABLE + "` WHERE `amount` = 42"));
        assertEquals(
                "APPLIED",
                str("SELECT `state` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` WHERE `id`='" + id
                        + "'"));
    }

    @Test
    @DisplayName("a migration still being driven is left alone")
    void freshClaimIsNotStolen() {

        // progress() refreshes the claim before every statement, so a live migration
        // renews this continuously. Taking it would mean two nodes issuing DDL
        // against one table.
        interruptAfter(MigrationStep.Phase.EXPAND, 0);

        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        assertEquals(0, report.found(), report.summary());
        assertEquals("varchar(40)", amountType(), "untouched");
    }

    @Test
    @DisplayName("two nodes sweeping at once: exactly one claims the row")
    void onlyOneNodeWins() {

        interruptAfter(MigrationStep.Phase.EXPAND, 600);

        List<InterruptedMigration> rows = MySQLMigrationSweeper.interrupted(ctx, DB, 60).block();
        assertEquals(1, rows.size());

        // A rolling deploy is exactly when several nodes sweep at the same moment and
        // exactly when interrupted rows exist, so a check-then-act would race
        // precisely where it is relied on. The guard is in the WHERE clause.
        Boolean first = MySQLMigrationSweeper.claim(ctx, rows.get(0), 60).block();
        Boolean second = MySQLMigrationSweeper.claim(ctx, rows.get(0), 60).block();

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    @DisplayName("a FAILED row is not swept, because it was refused rather than dropped")
    void failedIsNotSwept() {

        String id = interruptAfter(MigrationStep.Phase.EXPAND, 600);
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `state`='FAILED' WHERE `id`='" + id
                + "'");

        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        // FAILED usually means a data check said no and will say no again. Retrying
        // it on a timer would bury the rows that genuinely need help.
        assertEquals(0, report.found(), report.summary());
    }

    @Test
    @DisplayName("an APPLIED row is not swept")
    void appliedIsNotSwept() {

        String id = interruptAfter(MigrationStep.Phase.EXPAND, 600);
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `state`='APPLIED' WHERE `id`='" + id
                + "'");

        assertEquals(0, MySQLMigrationSweeper.sweep(ctx, 60).block().found());
    }

    @Test
    @DisplayName("a row with no replayable plan is reported rather than guessed at")
    void unplayableIsReported() {

        String id = interruptAfter(MigrationStep.Phase.EXPAND, 600);
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `plan` = NULL WHERE `id`='" + id + "'");

        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        // Rows written before the plan column existed look like this. Re-deriving a
        // plan from the current table would produce DIFFERENT steps, so the recorded
        // index would point into the wrong plan.
        assertEquals(1, report.found(), report.summary());
        assertEquals(0, report.claimed(), report.summary());
        assertEquals(1, report.unplayable().size(), report.summary());
        assertEquals("varchar(40)", amountType(), "and nothing was attempted");
    }

    @Test
    @DisplayName("the sweep finds tenants by their journals, not from a list of clients")
    void discoversSchemas() {

        exec("CREATE DATABASE IF NOT EXISTS `sweepdata_other`");
        MySQLMigrationJournal.ensure(ctx, "sweepdata_other").block();

        List<String> schemas = MySQLMigrationSweeper.schemas(ctx).block();

        // A tenant provisioned outside the normal path still gets recovered, for the
        // same reason the fan-out discovers tenants this way.
        assertTrue(schemas.contains(DB), String.valueOf(schemas));
        assertTrue(schemas.contains("sweepdata_other"), String.valueOf(schemas));

        exec("DROP DATABASE IF EXISTS `sweepdata_other`");
    }

    @Test
    @DisplayName("a quiet sweep says nothing happened")
    void quietWhenNothingToDo() {
        RecoveryReport report = MySQLMigrationSweeper.sweep(ctx, 60).block();

        assertTrue(report.isQuiet());
        assertEquals("no interrupted migrations", report.summary());
    }

    @Test
    @DisplayName("an older journal table gains the columns it is missing")
    void journalUpgradesItself() {

        // A table created before these columns existed. CREATE TABLE IF NOT EXISTS
        // does nothing to it, so without the upgrades the first statement naming one
        // fails - and the first such statement is inside recovery, which is the
        // worst place to find out.
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "`");
        // The journal exactly as the first version of it shipped: everything that
        // has been added since is absent, and nothing that was always there is.
        exec("CREATE TABLE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` ("
                + "`id` CHAR(26) NOT NULL, `storage_name` VARCHAR(255) NOT NULL,"
                + " `table_name` VARCHAR(255) NOT NULL, `from_version` INT NULL, `to_version` INT NOT NULL,"
                + " `surface` VARCHAR(16) NOT NULL, `phase` VARCHAR(20) NULL,"
                + " `statement_index` INT NOT NULL DEFAULT 0, `total_statements` INT NOT NULL,"
                + " `state` VARCHAR(20) NOT NULL, `started_at` DATETIME(3) NULL,"
                + " `finished_at` DATETIME(3) NULL, `error` TEXT NULL, `applied_by` VARCHAR(255) NULL,"
                + " PRIMARY KEY (`id`))");

        MySQLMigrationJournal.ensure(ctx, DB).block();

        for (String column :
                List.of("plan", "claimed_by", "claimed_at", "shape", "applied_count", "attempt", "resumed_at"))
            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                            + "' AND TABLE_NAME='" + MySQLMigrationJournal.TABLE + "' AND COLUMN_NAME='" + column
                            + "'"),
                    column + " should have been added");

        // And running it again changes nothing, because each add is guarded.
        MySQLMigrationJournal.ensure(ctx, DB).block();
        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='" + MySQLMigrationJournal.TABLE + "' AND COLUMN_NAME='plan'"));

        // Wide enough is not the same as usable: a real insert has to work, or the
        // upgrade has only moved the failure.
        exec(MySQLMigrationJournal.insert(
                DB, "01UPGRADE000000000000001", STORAGE, TABLE, 6, 7, SHAPE, "LIVE", 1, "kiran",
                MigrationPlanCodec.write(plan()), "node"));

        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "`"));
    }

    @Test
    @DisplayName("the plan survives a round trip through the journal")
    void planRoundTrips() {

        List<MigrationStep> original = plan();
        List<MigrationStep> back = MigrationPlanCodec.read(MigrationPlanCodec.write(original));

        assertEquals(original.size(), back.size());
        assertEquals(original, back, "a plan that does not come back exactly is not a plan");
    }

    @Test
    @DisplayName("damaged or absent plan JSON reads as empty rather than throwing")
    void damagedPlan() {
        // A sweep that throws on one bad row stops recovering the good ones.
        assertTrue(MigrationPlanCodec.read(null).isEmpty());
        assertTrue(MigrationPlanCodec.read("").isEmpty());
        assertTrue(MigrationPlanCodec.read("{not json").isEmpty());
    }
}
