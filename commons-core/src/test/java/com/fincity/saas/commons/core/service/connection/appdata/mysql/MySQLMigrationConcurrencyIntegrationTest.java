package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Two publishes of the same storage at the same moment.
 *
 * The journal used to decide whether to start by counting blocking rows and then
 * inserting its own - a check and an act with nothing between them, so two runs could
 * both count zero and both proceed. Re-running a plan is designed to be safe, which
 * is why this was survivable rather than catastrophic, but expand-contract has a
 * window where one run can rename the temporary column while the other is still
 * copying into it.
 *
 * A count cannot close that window. Only the database can, which is what the unique
 * index on the active key is for.
 */
@DisplayName("Concurrent publishes of one storage")
class MySQLMigrationConcurrencyIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "appdata_conc";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema(DB);
    }

    @BeforeEach
    void freshJournal() {
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

    private static String insertRunning(String id, String storage, String shape, String surface) {
        return MySQLMigrationJournal.insert(
                DB, id, storage, storage + "_tbl", 1, 2, shape, surface, 3, "tester", "[]", "node-" + id);
    }

    @Test
    @Timeout(300)
    @DisplayName("the database refuses the second RUNNING row for one storage, shape and surface")
    void onlyOneActiveRow() {
        // Issued as fast as the driver will take them. Whichever lands first wins;
        // the point is that the second cannot land at all.
        List<Boolean> results = Flux.range(0, 2)
                .parallel(2)
                .runOn(Schedulers.boundedElastic())
                .map(i -> {
                    try {
                        exec(insertRunning(String.format("%026d", i), "orders", "shapeA", "LIVE"));
                        return Boolean.TRUE;
                    } catch (Exception e) {
                        return Boolean.FALSE;
                    }
                })
                .sequential()
                .collectList()
                .block();

        assertEquals(
                1,
                results.stream().filter(Boolean::booleanValue).count(),
                "exactly one insert should succeed");

        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name` = 'orders' AND `state` IN ('PLANNED', 'RUNNING')"));
    }

    @Test
    @Timeout(300)
    @DisplayName("a different shape is not blocked, because it is a different migration")
    void differentShapeIsAllowed() {
        exec(insertRunning(String.format("%026d", 1), "orders", "shapeA", "LIVE"));
        exec(insertRunning(String.format("%026d", 2), "orders", "shapeB", "LIVE"));

        assertEquals(
                2,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name` = 'orders' AND `state` IN ('PLANNED', 'RUNNING')"));
    }

    @Test
    @Timeout(300)
    @DisplayName("and neither is the other surface, which migrates independently")
    void differentSurfaceIsAllowed() {
        exec(insertRunning(String.format("%026d", 3), "orders", "shapeA", "LIVE"));
        exec(insertRunning(String.format("%026d", 4), "orders", "shapeA", "DRAFT"));

        assertEquals(
                2,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name` = 'orders' AND `state` IN ('PLANNED', 'RUNNING')"));
    }

    @Test
    @Timeout(300)
    @DisplayName("once a run finishes, the next publish of the same shape is free to start")
    void finishedRowReleasesTheKey() {
        // The key is generated from the state, so it becomes NULL the moment the row
        // stops being RUNNING - and a unique index ignores NULLs. Nothing has to
        // remember to release anything.
        String first = String.format("%026d", 5);
        exec(insertRunning(first, "orders", "shapeA", "LIVE"));
        exec("UPDATE `" + DB + "`.`" + MySQLMigrationJournal.TABLE + "` SET `state` = 'APPLIED' WHERE `id` = '"
                + first + "'");

        exec(insertRunning(String.format("%026d", 6), "orders", "shapeA", "LIVE"));

        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM `" + DB + "`.`" + MySQLMigrationJournal.TABLE
                        + "` WHERE `storage_name` = 'orders' AND `state` IN ('PLANNED', 'RUNNING')"));
    }

    @Test
    @Timeout(300)
    @DisplayName("losing the race is reported as someone else's work, not as a failure")
    void lostRaceIsRecognised() {
        // Reported FAILED, a publish that is in fact proceeding would be blocked.
        // Reported APPLIED, this node would claim a table reached a shape it never
        // checked.
        Exception duplicate = null;
        exec(insertRunning(String.format("%026d", 7), "orders", "shapeA", "LIVE"));
        try {
            exec(insertRunning(String.format("%026d", 8), "orders", "shapeA", "LIVE"));
        } catch (Exception e) {
            duplicate = e;
        }

        assertTrue(duplicate != null, "the second insert must be refused");
        assertTrue(MySQLMigrationRunner.alreadyRunning(duplicate), duplicate.toString());
    }
}
