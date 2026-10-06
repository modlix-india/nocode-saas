package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The reconciler reports first and repairs only what it has been allowed to.
 *
 * The whole value is in the gate: an unapproved repair must be safe to run against
 * anything, and an approved one must be able to fix everything. A reconciler that
 * cannot be run without reading the report is a reconciler nobody runs.
 */
class MySQLDriftTest {

    private static final String DB = "T_app";
    private static final String TABLE = "thing";

    private static MySQLColumn col(String name, String type) {
        return new MySQLColumn(name, type, true);
    }

    private static MySQLDrift.Report report(
            List<MySQLColumn> existing, List<MySQLColumn> desired, List<String> idx, List<String> fks) {
        return MySQLDrift.of(DB, TABLE, true, existing, desired, idx, fks);
    }

    @Test
    @DisplayName("A table matching its definition reports clean and plans nothing")
    void cleanWhenInSync() {

        List<MySQLColumn> same = List.of(col("_id", "VARCHAR(40)"), col("title", "VARCHAR(100)"));

        MySQLDrift.Report r = report(same, same, List.of(), List.of());

        assertTrue(r.clean());
        assertFalse(r.needsApproval());
        assertTrue(MySQLDrift.repairStatements(r, false).isEmpty());
        assertTrue(MySQLDrift.repairStatements(r, true).isEmpty());
        assertTrue(r.summary().contains("in sync"));
    }

    @Test
    @DisplayName("A missing table is reported, and never silently created by a repair")
    void missingTableIsReported() {

        MySQLDrift.Report r = MySQLDrift.of(DB, TABLE, false, List.of(), List.of(col("_id", "VARCHAR(40)")),
                List.of(), List.of());

        assertFalse(r.clean());
        assertTrue(r.needsApproval(), "a missing table is not something to fix without being asked");
        assertTrue(r.summary().contains("MISSING"));
    }

    @Nested
    @DisplayName("The approval gate")
    class ApprovalGate {

        @Test
        @DisplayName("Without approval, additive index and key work still runs")
        void additiveWorkNeedsNoApproval() {

            List<String> idx = List.of("CREATE INDEX `rel_x` ON `T_app`.`thing` (`x`)");
            List<String> fks = List.of("ALTER TABLE `T_app`.`thing` ADD CONSTRAINT `fk_x` FOREIGN KEY (`x`) REFERENCES `o` (`_id`)");

            MySQLDrift.Report r = report(List.of(col("_id", "VARCHAR(40)")), List.of(col("_id", "VARCHAR(40)")), idx, fks);

            assertFalse(r.needsApproval());
            assertEquals(2, MySQLDrift.repairStatements(r, false).size());
        }

        @Test
        @DisplayName("Without approval, every DROP is withheld")
        void dropsAreWithheld() {

            List<String> idx = List.of(
                    "DROP INDEX `stale` ON `T_app`.`thing`",
                    "CREATE INDEX `rel_x` ON `T_app`.`thing` (`x`)");
            List<String> fks = List.of("ALTER TABLE `T_app`.`thing` DROP FOREIGN KEY `fk_old`");

            MySQLDrift.Report r = report(List.of(col("_id", "VARCHAR(40)")), List.of(col("_id", "VARCHAR(40)")), idx, fks);

            assertTrue(r.needsApproval());
            assertEquals(2, r.drops().size());

            List<String> unapproved = MySQLDrift.repairStatements(r, false);
            assertEquals(1, unapproved.size());
            assertTrue(unapproved.getFirst().startsWith("CREATE INDEX"));

            assertEquals(3, MySQLDrift.repairStatements(r, true).size());
        }

        /**
         * A column the definition no longer has is the single most destructive thing
         * this can do, and it is exactly what somebody running a reconciler to "tidy
         * up" would not expect.
         */
        @Test
        @DisplayName("Without approval, a column is never dropped or narrowed")
        void destructiveColumnWorkIsWithheld() {

            List<MySQLColumn> existing = List.of(col("_id", "VARCHAR(40)"), col("gone", "VARCHAR(100)"));
            List<MySQLColumn> desired = List.of(col("_id", "VARCHAR(40)"));

            MySQLDrift.Report r = report(existing, desired, List.of(), List.of());

            assertFalse(r.clean());
            assertTrue(r.needsApproval());
            assertFalse(r.dangerous().isEmpty());
            assertTrue(
                    MySQLDrift.repairStatements(r, false).isEmpty(),
                    "an unapproved repair must not touch a column that holds data");

            List<String> approved = MySQLDrift.repairStatements(r, true);
            assertEquals(1, approved.size());
            assertTrue(approved.getFirst().contains("DROP COLUMN `gone`"));
        }

        @Test
        @DisplayName("A missing column is additive, so it is added without approval")
        void missingColumnIsAdditive() {

            List<MySQLColumn> existing = List.of(col("_id", "VARCHAR(40)"));
            List<MySQLColumn> desired = List.of(col("_id", "VARCHAR(40)"), col("title", "VARCHAR(100)"));

            MySQLDrift.Report r = report(existing, desired, List.of(), List.of());

            List<String> unapproved = MySQLDrift.repairStatements(r, false);
            assertEquals(1, unapproved.size());
            assertTrue(unapproved.getFirst().contains("ADD COLUMN `title`"));
        }
    }

    @Nested
    @DisplayName("The order the plan runs in")
    class PlanOrder {

        /**
         * An index backing a foreign key cannot be dropped while the key is still on
         * it, so the key statements have to come first or an approved repair fails
         * halfway.
         */
        @Test
        @DisplayName("Dropped keys come before the indexes they sit on")
        void keysBeforeIndexes() {

            MySQLDrift.Report r = report(
                    List.of(col("_id", "VARCHAR(40)")),
                    List.of(col("_id", "VARCHAR(40)")),
                    List.of("DROP INDEX `rel_x` ON `T_app`.`thing`"),
                    List.of("ALTER TABLE `T_app`.`thing` DROP FOREIGN KEY `fk_x`"));

            List<String> plan = MySQLDrift.repairStatements(r, true);

            assertTrue(plan.get(0).contains("DROP FOREIGN KEY"));
            assertTrue(plan.get(1).startsWith("DROP INDEX"));
        }

        /**
         * The other half, and the one that was wrong: adding a field and an index on
         * it in the same edit planned the index FIRST, against a column that did not
         * exist yet. MySQL fails the statement outright, so an unapproved repair -
         * the one anybody can run - died on its own plan.
         */
        @Test
        @DisplayName("A new column comes before the index and the key that need it")
        void columnsBeforeTheThingsThatNeedThem() {

            MySQLDrift.Report r = report(
                    List.of(col("_id", "VARCHAR(40)")),
                    List.of(col("_id", "VARCHAR(40)"), col("owner", "CHAR(26)")),
                    List.of("CREATE INDEX `rel_owner` ON `T_app`.`thing` (`owner`)"),
                    List.of("ALTER TABLE `T_app`.`thing` ADD CONSTRAINT `fk_owner` FOREIGN KEY (`owner`)"
                            + " REFERENCES `o` (`_id`)"));

            List<String> plan = MySQLDrift.repairStatements(r, false);

            assertEquals(3, plan.size());
            assertTrue(plan.get(0).contains("ADD COLUMN `owner`"), "the column has to exist first");
            assertTrue(plan.get(1).startsWith("CREATE INDEX"), "then the index on it");
            assertTrue(plan.get(2).contains("ADD CONSTRAINT"), "then the key that needs the index");
        }

        /**
         * Both rules at once, which is the shape a real reconcile produces: something
         * comes off, the columns move, and something goes back on.
         */
        @Test
        @DisplayName("Drops, then columns, then adds")
        void outsideIn() {

            MySQLDrift.Report r = report(
                    List.of(col("_id", "VARCHAR(40)")),
                    List.of(col("_id", "VARCHAR(40)"), col("owner", "CHAR(26)")),
                    List.of(
                            "DROP INDEX `stale` ON `T_app`.`thing`",
                            "CREATE INDEX `rel_owner` ON `T_app`.`thing` (`owner`)"),
                    List.of(
                            "ALTER TABLE `T_app`.`thing` DROP FOREIGN KEY `fk_old`",
                            "ALTER TABLE `T_app`.`thing` ADD CONSTRAINT `fk_owner` FOREIGN KEY (`owner`)"
                                    + " REFERENCES `o` (`_id`)"));

            List<String> plan = MySQLDrift.repairStatements(r, true);

            assertEquals(5, plan.size());
            assertTrue(plan.get(0).contains("DROP FOREIGN KEY"));
            assertTrue(plan.get(1).startsWith("DROP INDEX"));
            assertTrue(plan.get(2).contains("ADD COLUMN `owner`"));
            assertTrue(plan.get(3).startsWith("CREATE INDEX"));
            assertTrue(plan.get(4).contains("ADD CONSTRAINT"));
        }
    }
}
