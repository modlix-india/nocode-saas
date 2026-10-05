package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
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

/** The runner and its journal, against a real MySQL. */
@Testcontainers
class MySQLMigrationRunnerIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "appdata";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema(DB);
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

    private static void makeTable(String table, String... amounts) {
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + table + "`");
        exec("CREATE TABLE `" + DB + "`.`" + table + "` (`_id` CHAR(26) NOT NULL, `amount` VARCHAR(40) NULL,"
                + " PRIMARY KEY (`_id`))");
        int i = 0;
        for (String a : amounts)
            exec("INSERT INTO `" + DB + "`.`" + table + "` VALUES ('" + String.format("%026d", i++) + "', "
                    + (a == null ? "NULL" : "'" + a + "'") + ")");
    }

    private static List<MigrationStep> narrowing(String table) {
        return MySQLMigrationPlanner.plan(
                DB, table, 7, List.of(new SchemaChange(Kind.NARROWING, "amount", "VARCHAR(40)", "DOUBLE", "retype")));
    }

    /** The shape this plan is heading for, which is what the journal keys on. */
    private static final String SHAPE = MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("amount", "DOUBLE", true)));

    private static MigrationOutcome run(String storage, String table, List<MigrationStep> plan) {
        return run(storage, table, plan, SHAPE);
    }

    private static MigrationOutcome run(String storage, String table, List<MigrationStep> plan, String shape) {
        return MySQLMigrationRunner.run(ctx, DB, storage, table, 6, 7, shape, "LIVE", plan, "kiran")
                .block();
    }

    private static void clearJournal(String storage) {
        MySQLMigrationJournal.ensure(ctx, DB).block();
        exec("DELETE FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` WHERE `storage_name` = '" + storage
                + "'");
    }

    @Test
    @DisplayName("a clean migration is journalled as APPLIED with a finish time")
    void successIsJournalled() {
        clearJournal("s_ok");
        makeTable("r_ok", "1", "2.5");

        MigrationOutcome out = run("s_ok", "r_ok", narrowing("r_ok"));

        assertTrue(out.isSuccess(), out.error());
        assertEquals("double", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_ok' AND COLUMN_NAME='amount'"));

        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_ok' AND `state`='APPLIED' AND `finished_at` IS NOT NULL"));
    }

    @Test
    @DisplayName("dirty data blocks the run, and the journal says which check stopped it")
    void blockedIsJournalled() {
        clearJournal("s_bad");
        makeTable("r_bad", "1", "junk");

        MigrationOutcome out = run("s_bad", "r_bad", narrowing("r_bad"));

        assertEquals(MigrationState.FAILED, out.state());
        assertEquals(1, out.offending(), "one junk row");
        assertNotNull(out.blockedBy());

        // Nothing was altered: the pre-flight is the first step.
        assertEquals("varchar(40)", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_bad' AND COLUMN_NAME='amount'"));

        assertTrue(str("SELECT `error` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_bad'")
                .contains("would not survive"));
    }

    @Test
    @DisplayName("an unresolved failure blocks a migration to a DIFFERENT shape")
    void failClosedAcrossShapes() {
        clearJournal("s_closed");
        makeTable("r_closed", "1", "junk");

        assertEquals(MigrationState.FAILED, run("s_closed", "r_closed", narrowing("r_closed")).state());

        // Retyping to INT is a different plan heading for a different shape. Letting
        // it start on top of a half-finished retype to DOUBLE is the case fail-closed
        // exists for: two partial migrations interleaving on one table is where data
        // actually goes missing.
        MigrationOutcome later = MySQLMigrationRunner.run(
                        ctx, DB, "s_closed", "r_closed", 7, 8,
                        MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("amount", "INT", true))), "LIVE",
                        MySQLMigrationPlanner.plan(
                                DB, "r_closed", 8,
                                List.of(new SchemaChange(Kind.NARROWING, "amount", "VARCHAR(40)", "INT", "retype"))),
                        "kiran")
                .block();

        assertEquals(MigrationState.NEEDS_ATTENTION, later.state());
        assertTrue(later.error().contains("unresolved"), later.error());
        assertEquals("varchar(40)", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_closed' AND COLUMN_NAME='amount'"));
    }

    @Test
    @DisplayName("retrying the SAME migration is not blocked by its own earlier failure")
    void sameVersionRetriesAfterItsOwnFailure() {
        clearJournal("s_retry");
        makeTable("r_retry", "1", "junk");

        assertEquals(MigrationState.FAILED, run("s_retry", "r_retry", narrowing("r_retry")).state());

        // Fixing the data and running the same publish again is what anyone would do,
        // and it has to be enough. Requiring an operator to edit a journal row first
        // would mean every transient failure needs a human before it can even be
        // retried, which across 71 tenants is not a recovery procedure anyone follows.
        exec("UPDATE `" + DB + "`.`r_retry` SET `amount` = '2' WHERE `amount` = 'junk'");

        MigrationOutcome second = run("s_retry", "r_retry", narrowing("r_retry"));

        assertTrue(second.isSuccess(), second.error());
        assertTrue(second.wasResumed(), "the retry should report that it continued an earlier attempt");
        assertEquals("double", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_retry' AND COLUMN_NAME='amount'"));
    }

    @Test
    @DisplayName("a retry continues the same journal row and counts the attempt")
    void retryKeepsOneRow() {
        clearJournal("s_one_row");
        makeTable("r_one_row", "1", "junk");

        run("s_one_row", "r_one_row", narrowing("r_one_row"));
        exec("UPDATE `" + DB + "`.`r_one_row` SET `amount` = '5' WHERE `amount` = 'junk'");
        run("s_one_row", "r_one_row", narrowing("r_one_row"));

        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_one_row'"),
                "two attempts at one migration are one migration, not two");
        assertEquals(
                2,
                scalar("SELECT `attempt` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_one_row'"));
        assertEquals(
                "APPLIED",
                str("SELECT `state` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_one_row'"));
        assertNotNull(str("SELECT `resumed_at` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_one_row'"));
    }

    @Test
    @DisplayName("a crash DURING contract is resumed, where restarting from the top could not be")
    void resumesFromInsideContract() {
        clearJournal("s_contract");
        makeTable("r_contract", "9");

        List<MigrationStep> plan = narrowing("r_contract");

        // Run everything up to and including the first CONTRACT step, which drops the
        // original column, then stop as a crash would. The journal is written by hand
        // because the point is what a half-finished row looks like, not how it got
        // there.
        int stopAfter = -1;
        for (int i = 0; i < plan.size(); i++) {
            MigrationStep st = plan.get(i);
            if (st.sql() == null) continue;
            exec(st.sql());
            if (st.phase() == MigrationStep.Phase.CONTRACT) {
                stopAfter = i;
                break;
            }
        }
        assertTrue(stopAfter > 0);

        MySQLMigrationJournal.ensure(ctx, DB).block();
        exec(MySQLMigrationJournal.insert(
                DB, "01JCRASHCRASHCRASHCRASH01", "s_contract", "r_contract", 6, 7, SHAPE, "LIVE", plan.size(),
                "kiran", MigrationPlanCodec.write(plan), "dead-node"));
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `state`='RUNNING', `statement_index`="
                + stopAfter + " WHERE `id`='01JCRASHCRASHCRASHCRASH01'");

        // `amount` is gone at this point, so the plan's pre-flight - which reads it -
        // is no longer a statement MySQL will accept. A resume that started from the
        // beginning would die here on a migration that is one statement from done.
        assertEquals(
                0,
                scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='r_contract' AND COLUMN_NAME='amount'"));

        MigrationOutcome out = run("s_contract", "r_contract", plan);

        assertTrue(out.isSuccess(), out.error());
        assertEquals(stopAfter, out.resumedFrom());
        assertEquals("double", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_contract' AND COLUMN_NAME='amount'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`r_contract` WHERE `amount` = 9"));
    }

    @Test
    @DisplayName("the journal counts statements applied across every attempt, not just the last")
    void appliedCountAccumulates() {
        clearJournal("s_count");
        makeTable("r_count", "1", "junk");

        run("s_count", "r_count", narrowing("r_count"));
        long afterFailure = scalar("SELECT `applied_count` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_count'");

        exec("UPDATE `" + DB + "`.`r_count` SET `amount` = '6' WHERE `amount` = 'junk'");
        run("s_count", "r_count", narrowing("r_count"));

        long afterSuccess = scalar("SELECT `applied_count` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_count'");

        assertTrue(
                afterSuccess > afterFailure,
                "how much work this migration did is the sum of its attempts, " + afterFailure + " then "
                        + afterSuccess);
    }

    @Test
    @DisplayName("once the failure is resolved the same plan runs through")
    void resolvingUnblocks() {
        clearJournal("s_fixed");
        makeTable("r_fixed", "1", "junk");

        run("s_fixed", "r_fixed", narrowing("r_fixed"));
        exec("UPDATE `" + DB + "`.`r_fixed` SET `amount` = '2' WHERE `amount` = 'junk'");

        // Acknowledging the old failure is what an operator would do.
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` SET `state` = 'ABANDONED' WHERE `storage_name` = 's_fixed'");

        assertTrue(run("s_fixed", "r_fixed", narrowing("r_fixed")).isSuccess());
        assertEquals("double", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_fixed' AND COLUMN_NAME='amount'"));
    }

    @Test
    @DisplayName("a plan the journal already recorded as APPLIED is skipped whole")
    void completedPlanIsNotRepeated() {
        clearJournal("s_again");
        makeTable("r_again", "3");

        MigrationOutcome first = run("s_again", "r_again", narrowing("r_again"));
        assertTrue(first.applied() > 0);

        MigrationOutcome second = run("s_again", "r_again", narrowing("r_again"));

        assertTrue(second.isSuccess(), second.error());
        assertEquals(0, second.applied(), "a finished plan must not execute anything again");

        // Step preconditions alone would NOT have caught this: once CONTRACT renames the
        // temporary column into place, EXPAND's precondition sees it missing again and
        // the whole expand-contract sequence would repeat, dropping and recreating a
        // column that was already correct. Only the journal knows the plan finished.
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_again'"), "no second journal row");
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`r_again` WHERE `amount` = 3"));
    }

    @Test
    @DisplayName("a plan interrupted part way is finished by re-running it")
    void partialPlanResumes() {
        clearJournal("s_resume");
        makeTable("r_resume", "8");

        // Execute only up to the expand, as a crash would leave it, with no journal row
        // claiming completion.
        for (MigrationStep st : narrowing("r_resume")) {
            if (st.sql() == null) continue;
            exec(st.sql());
            if (st.phase() == MigrationStep.Phase.EXPAND) break;
        }
        assertEquals("varchar(40)", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_resume' AND COLUMN_NAME='amount'"));

        MigrationOutcome out = run("s_resume", "r_resume", narrowing("r_resume"));

        assertTrue(out.isSuccess(), out.error());
        assertTrue(out.skipped() > 0, "the already-applied expand should be skipped");
        assertEquals("double", str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                + "' AND TABLE_NAME='r_resume' AND COLUMN_NAME='amount'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`r_resume` WHERE `amount` = 8"));
    }

    @Test
    @DisplayName("a referenced schema changing re-migrates, even though the version has not moved")
    void shapeChangeWithoutVersionChange() {
        clearJournal("s_schema");
        makeTable("r_schema", "4");

        // Publish once: amount becomes a DOUBLE.
        assertTrue(run("s_schema", "r_schema", narrowing("r_schema")).isSuccess());

        // Now a SCHEMA the storage references gains a field. The storage itself was
        // not touched, so its version is still 7 - and that is the whole problem.
        // Keyed on the version, this second migration finds a row already marked
        // applied and does nothing, leaving the table a column short against a
        // definition that says it is there.
        List<MigrationStep> addColumn = MySQLMigrationPlanner.plan(
                DB, "r_schema", 7,
                List.of(new SchemaChange(Kind.WIDENING, "currency", null, "VARCHAR(3)", "new field from the schema")));

        String newShape = MySQLTablePlanner.shapeOf(
                List.of(new MySQLColumn("amount", "DOUBLE", true), new MySQLColumn("currency", "VARCHAR(3)", true)));

        MigrationOutcome out = run("s_schema", "r_schema", addColumn, newShape);

        assertTrue(out.isSuccess(), out.error());
        assertTrue(out.applied() > 0, "the shape changed, so there is work to do");
        assertEquals(
                "varchar(3)",
                str("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='r_schema' AND COLUMN_NAME='currency'"));

        // Two shapes, two rows. The first one stays as the record of what it did.
        assertEquals(
                2,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_schema'"));
    }

    @Test
    @DisplayName("the same shape at the same version is still a no-op")
    void sameShapeStillSkips() {
        clearJournal("s_same");
        makeTable("r_same", "4");

        run("s_same", "r_same", narrowing("r_same"));

        // Keying on shape must not have cost the idempotency that keying on version
        // bought: a publish that changes nothing must still execute nothing.
        MigrationOutcome second = run("s_same", "r_same", narrowing("r_same"));

        assertTrue(second.isSuccess(), second.error());
        assertEquals(0, second.applied());
        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_same'"));
    }

    @Test
    @DisplayName("the journal records progress before the work, so a crash is traceable")
    void journalIsWrittenAhead() {
        clearJournal("s_track");
        makeTable("r_track", "4");

        run("s_track", "r_track", narrowing("r_track"));

        long total = scalar("SELECT `total_statements` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_track'");
        assertEquals(narrowing("r_track").size(), total, "the plan size is recorded up front");

        assertNotNull(str("SELECT `started_at` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_track'"));
        assertEquals(
                "kiran",
                str("SELECT `applied_by` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_track'"));
    }

    @Test
    @DisplayName("an empty plan is a no-op and writes no journal row")
    void emptyPlanWritesNothing() {
        clearJournal("s_empty");
        MigrationOutcome out = MySQLMigrationRunner.run(ctx, DB, "s_empty", "r_empty", 1, 2, SHAPE, "LIVE", List.of(), "k")
                .block();
        assertTrue(out.isSuccess());
        assertEquals(0, scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                + "` WHERE `storage_name`='s_empty'"));
    }

    @Test
    @DisplayName("a quote in the actor name does not break the journal insert")
    void escapesFreeText() {
        clearJournal("s_quote");
        makeTable("r_quote", "9");

        MigrationOutcome out = MySQLMigrationRunner.run(
                        ctx, DB, "s_quote", "r_quote", 1, 7, SHAPE, "LIVE", narrowing("r_quote"), "o'brien")
                .block();

        assertTrue(out.isSuccess(), out.error());
        assertEquals(
                "o'brien",
                str("SELECT `applied_by` FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name`='s_quote'"));
    }
}
