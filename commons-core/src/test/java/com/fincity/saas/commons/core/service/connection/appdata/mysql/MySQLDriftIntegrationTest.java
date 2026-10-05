package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Drift introduced by hand, against a real table, then detected and repaired.
 *
 * The unit tests cover the classification; this covers the part that can only be
 * wrong against a live server - that what we inspect matches what we created, and
 * that the DDL we plan actually applies in the order we plan it.
 */
class MySQLDriftIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "drift_db";
    private static final String TABLE = "thing";

    /**
     * The declared shape, WITHOUT _id. createTable supplies the primary key itself
     * and MySQLTableInspector filters it back out, so a definition that mentions it
     * reads as a column the table is missing.
     */
    private static final List<MySQLColumn> DEFINITION = List.of(
            new MySQLColumn("title", "VARCHAR(100)", true), new MySQLColumn("qty", "INT", true));

    @BeforeEach
    void freshTable() {
        schema(DB);
        Mono.from(mysql().query("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`")).block();
        Mono.from(mysql().query(MySQLTablePlanner.createTable(DB + "`.`" + TABLE, DEFINITION))).block();
    }

    private static List<MySQLColumn> live() {
        return MySQLTableInspector.columns(mysql(), DB, TABLE).block();
    }

    private static MySQLDrift.Report report() {
        return MySQLDrift.of(DB, TABLE, true, live(), DEFINITION, List.of(), List.of());
    }

    private static void run(List<String> statements) {
        for (String s : statements) Mono.from(mysql().query(s)).block();
    }

    @Test
    @DisplayName("A table built from its definition reports clean")
    void freshTableIsClean() {
        assertTrue(report().clean(), () -> "unexpected drift: " + report().summary());
    }

    @Test
    @DisplayName("A column the definition lost is reported but survives an unapproved repair")
    void extraColumnNeedsApproval() {

        Mono.from(mysql().query("ALTER TABLE `" + DB + "`.`" + TABLE + "` ADD COLUMN `rogue` VARCHAR(10)")).block();

        MySQLDrift.Report before = report();
        assertFalse(before.clean());
        assertTrue(before.needsApproval());

        run(MySQLDrift.repairStatements(before, false));
        assertTrue(
                live().stream().anyMatch(c -> "rogue".equals(c.name())),
                "an unapproved repair must not drop a column that may hold data");

        run(MySQLDrift.repairStatements(report(), true));
        assertFalse(live().stream().anyMatch(c -> "rogue".equals(c.name())));
        assertTrue(report().clean());
    }

    @Test
    @DisplayName("A column the table is missing is added without approval, and that is enough to go clean")
    void missingColumnIsRepairedUnapproved() {

        Mono.from(mysql().query("ALTER TABLE `" + DB + "`.`" + TABLE + "` DROP COLUMN `qty`")).block();

        MySQLDrift.Report before = report();
        assertFalse(before.clean());
        assertFalse(before.needsApproval(), "adding a missing column loses nothing");

        run(MySQLDrift.repairStatements(before, false));

        assertTrue(live().stream().anyMatch(c -> "qty".equals(c.name())));
        assertTrue(report().clean());
    }

    @Test
    @DisplayName("A table that is simply gone is reported, not quietly rebuilt")
    void missingTableIsReported() {

        Mono.from(mysql().query("DROP TABLE `" + DB + "`.`" + TABLE + "`")).block();

        boolean exists = MySQLTableInspector.tableExists(mysql(), DB, TABLE).block();
        assertFalse(exists);

        MySQLDrift.Report r = MySQLDrift.of(DB, TABLE, false, List.of(), DEFINITION, List.of(), List.of());
        assertTrue(r.needsApproval());
        assertTrue(MySQLDrift.repairStatements(r, false).isEmpty());
    }
}
