package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * The migration plan, executed against a real MySQL with dirty data.
 *
 * This is the test the whole phase exists for. A plan that renders plausibly and a plan
 * that survives MySQL's strict mode on a column of mixed junk are different things.
 */
@Testcontainers
class MySQLMigrationIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "appdata";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("appdata");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private static String columnType(String table, String column) {
        return Mono.from(ctx.resultQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                        + DB + "' AND TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .block();
    }

    /** A table with a string amount column, seeded with the given values. */
    private static void makeTable(String table, String... amounts) {
        exec("DROP TABLE IF EXISTS `" + DB + "`.`" + table + "`");
        exec("CREATE TABLE `" + DB + "`.`" + table + "` (`_id` CHAR(26) NOT NULL, `amount` VARCHAR(40) NULL,"
                + " PRIMARY KEY (`_id`))");
        int i = 0;
        for (String a : amounts) {
            String v = a == null ? "NULL" : "'" + a + "'";
            exec("INSERT INTO `" + DB + "`.`" + table + "` VALUES ('" + String.format("%026d", i++) + "', " + v + ")");
        }
    }

    /** Run a plan the way the runner will: skip steps whose precondition says so. */
    private static void run(List<MigrationStep> plan) {
        for (MigrationStep s : plan) {
            if (s.sql() == null) continue;
            if (s.precondition() != null && scalar(s.precondition()) == 0) continue;
            exec(s.sql());
        }
    }

    private static List<MigrationStep> narrowingPlan(String table) {
        return MySQLMigrationPlanner.plan(
                DB,
                table,
                7,
                List.of(new SchemaChange(Kind.NARROWING, "amount", "VARCHAR(40)", "DOUBLE", "retype")));
    }

    @Test
    @DisplayName("clean data: the column really becomes a DOUBLE and the values survive")
    void happyPath() {
        makeTable("m_clean", "10.5", "20", "30.25");

        List<MigrationStep> plan = narrowingPlan("m_clean");

        // Only the FIRST check is a pre-flight: it reads the original column alone and
        // so can run before any DDL. The later one needs the temporary column.
        MigrationStep preflight = plan.stream()
                .filter(s -> s.dataCheck() != null)
                .findFirst()
                .orElseThrow();
        assertEquals(0, scalar(preflight.dataCheck()), "clean data should fail nothing");

        run(plan);

        assertEquals("double", columnType("m_clean", "amount"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_clean` WHERE `amount` IS NOT NULL"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_clean` WHERE `amount` = 20"));
    }

    @Test
    @DisplayName("dirty data: the VERIFY step catches the rows that cannot convert")
    void dirtyDataIsCaught() {
        // The scenario from the plan: a string column that picked up non-numeric junk.
        makeTable("m_dirty", "10.5", "not-a-number", "30");

        List<MigrationStep> plan = narrowingPlan("m_dirty");

        // The pre-flight catches it before a single ALTER is issued, which is what lets
        // a publish check all 71 tenants and then decide.
        MigrationStep preflight = plan.stream()
                .filter(s -> s.dataCheck() != null)
                .findFirst()
                .orElseThrow();

        assertEquals(1, scalar(preflight.dataCheck()), "the junk row must be counted before anything runs");

        assertEquals(
                "varchar(40)",
                columnType("m_dirty", "amount"),
                "the pre-flight must not have touched the table");
    }

    @Test
    @DisplayName("the old column is still intact when VERIFY fails, so nothing is lost")
    void failureLeavesDataIntact() {
        makeTable("m_intact", "10.5", "junk", "30");

        // Push past the pre-flight deliberately and run the DDL anyway, to prove that
        // even then the original column is intact when the second check fails.
        for (MigrationStep s : narrowingPlan("m_intact")) {
            if (s.phase() == MigrationStep.Phase.CONTRACT) break;
            if (s.sql() == null) continue;
            if (s.precondition() != null && scalar(s.precondition()) == 0) continue;
            exec(s.sql());
        }

        assertEquals("varchar(40)", columnType("m_intact", "amount"), "the original column must be untouched");
        assertEquals(3, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_intact` WHERE `amount` IS NOT NULL"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_intact` WHERE `amount` = 'junk'"));
    }

    @Test
    @DisplayName("re-running a completed plan is a no-op, which is the recovery path")
    void planIsRerunnable() {
        makeTable("m_rerun", "1", "2");

        run(narrowingPlan("m_rerun"));
        assertEquals("double", columnType("m_rerun", "amount"));

        // Every step is guarded, so the second pass should skip all of them rather than
        // failing on a column that already exists.
        run(narrowingPlan("m_rerun"));
        assertEquals("double", columnType("m_rerun", "amount"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_rerun`"));
    }

    @Test
    @DisplayName("a plan interrupted part way is completed by re-running it")
    void partialPlanResumes() {
        makeTable("m_resume", "5", "6");

        List<MigrationStep> plan = narrowingPlan("m_resume");

        // Stop after the expand, simulating a crash mid-migration.
        for (MigrationStep s : plan) {
            if (s.sql() == null) continue;
            if (s.precondition() != null && scalar(s.precondition()) == 0) continue;
            exec(s.sql());
            if (s.phase() == MigrationStep.Phase.EXPAND) break;
        }
        assertEquals("varchar(40)", columnType("m_resume", "amount"), "half way: original still there");

        run(narrowingPlan("m_resume"));

        assertEquals("double", columnType("m_resume", "amount"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_resume` WHERE `amount` = 5"));
    }

    @Test
    @DisplayName("a destructive plan snapshots the table before dropping anything")
    void dropIsSnapshotted() {
        makeTable("m_drop", "1", "2");

        run(MySQLMigrationPlanner.plan(
                DB, "m_drop", 7, List.of(new SchemaChange(Kind.DESTRUCTIVE, "amount", "VARCHAR(40)", null, "drop"))));

        assertEquals(2, scalar("SELECT COUNT(*) FROM `" + DB + "`.`m_drop__bak_7`"), "the snapshot holds the rows");
        assertEquals(
                0,
                scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='m_drop' AND COLUMN_NAME='amount'"),
                "the column is gone from the live table");
        assertEquals(
                1,
                scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                        + "' AND TABLE_NAME='m_drop__bak_7' AND COLUMN_NAME='amount'"),
                "but recoverable from the snapshot");
    }
}
