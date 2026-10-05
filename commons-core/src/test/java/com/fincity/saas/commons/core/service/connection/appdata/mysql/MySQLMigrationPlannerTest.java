package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.MigrationStep.Phase;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.SchemaChange.Kind;

class MySQLMigrationPlannerTest {

    private static final String DB = "LZCLA_leadzump";
    private static final String T = "sales";

    private static List<MigrationStep> plan(SchemaChange... changes) {
        return MySQLMigrationPlanner.plan(DB, T, 7, List.of(changes));
    }

    private static SchemaChange widen(String col, String from, String to) {
        return new SchemaChange(Kind.WIDENING, col, from, to, "test");
    }

    private static SchemaChange narrow(String col, String from, String to) {
        return new SchemaChange(Kind.NARROWING, col, from, to, "test");
    }

    private static SchemaChange drop(String col, String from) {
        return new SchemaChange(Kind.DESTRUCTIVE, col, from, null, "test");
    }

    private static List<Phase> phases(List<MigrationStep> steps) {
        return steps.stream().map(MigrationStep::phase).toList();
    }

    @Nested
    @DisplayName("widening")
    class Widening {

        @Test
        @DisplayName("a new nullable column is one statement with no data check")
        void newColumn() {
            List<MigrationStep> p = plan(widen("region", null, "VARCHAR(40)"));
            assertEquals(1, p.size());
            assertTrue(p.getFirst().sql().contains("ADD COLUMN `region` VARCHAR(40) NULL"));
            assertNull(p.getFirst().dataCheck(), "a widening cannot lose data");
        }

        @Test
        @DisplayName("widening an existing column modifies it in place, which is safe by classification")
        void modifyInPlace() {
            List<MigrationStep> p = plan(widen("code", "VARCHAR(10)", "VARCHAR(50)"));
            assertEquals(1, p.size());
            assertTrue(p.getFirst().sql().contains("MODIFY COLUMN `code` VARCHAR(50)"));
        }

        @Test
        @DisplayName("no snapshot is taken when nothing can be lost")
        void noSnapshotForSafePlans() {
            assertTrue(phases(plan(widen("a", null, "INT"), widen("b", null, "INT")))
                    .stream()
                    .noneMatch(ph -> ph == Phase.SNAPSHOT));
        }
    }

    @Nested
    @DisplayName("narrowing uses expand-contract")
    class Narrowing {

        @Test
        @DisplayName("the string-to-number case produces the full safe sequence")
        void theCaseThatStartedAllThis() {
            List<MigrationStep> p = plan(narrow("amount", "VARCHAR(40)", "DOUBLE"));

            // VERIFY comes first and is expressed over the ORIGINAL column, so every
            // tenant can be checked before any is touched. The second VERIFY after the
            // backfill is the belt-and-braces one.
            assertEquals(
                    List.of(Phase.SNAPSHOT, Phase.PREFLIGHT, Phase.EXPAND, Phase.BACKFILL, Phase.VERIFY,
                            Phase.CONTRACT, Phase.CONTRACT),
                    phases(p));
        }

        @Test
        @DisplayName("the old column survives until VERIFY has passed")
        void oldColumnSurvivesUntilVerified() {
            List<MigrationStep> p = plan(narrow("amount", "VARCHAR(40)", "DOUBLE"));

            int verify = phases(p).indexOf(Phase.VERIFY);
            int firstDrop = -1;
            for (int i = 0; i < p.size(); i++)
                if (p.get(i).sql() != null && p.get(i).sql().contains("DROP COLUMN `amount`")) firstDrop = i;

            assertTrue(verify < firstDrop, "nothing destructive may precede the check");
        }

        @Test
        @DisplayName("the first VERIFY is a pre-flight over the original column only")
        void preflightTouchesOnlyTheOriginalColumn() {
            // This is what makes the publish fan-out possible: it can be run against all
            // 71 tenants before a single ALTER is issued anywhere.
            MigrationStep preflight = plan(narrow("amount", "VARCHAR(40)", "DOUBLE")).stream()
                    .filter(s -> s.phase() == Phase.PREFLIGHT)
                    .findFirst()
                    .orElseThrow();

            assertNotNull(preflight.dataCheck());
            assertTrue(preflight.dataCheck().contains("`amount` IS NOT NULL"), preflight.dataCheck());
            assertTrue(
                    !preflight.dataCheck().contains("amount__v"),
                    "a pre-flight that needs the temporary column cannot run before the migration");
        }

        @Test
        @DisplayName("the backfill copies only rows that will convert")
        void backfillFiltersToConvertibleRows() {
            // MySQL strict mode aborts the statement on an unconvertible value rather
            // than writing NULL, so an unfiltered copy dies on the first bad row.
            MigrationStep backfill = plan(narrow("amount", "VARCHAR(40)", "DOUBLE")).stream()
                    .filter(s -> s.phase() == Phase.BACKFILL)
                    .findFirst()
                    .orElseThrow();
            assertTrue(backfill.sql().contains("REGEXP"), backfill.sql());
        }

        @Test
        @DisplayName("the backfill is DML, which is the one part MySQL can roll back")
        void backfillIsDml() {
            MigrationStep backfill = plan(narrow("amount", "VARCHAR(40)", "DOUBLE")).stream()
                    .filter(s -> s.phase() == Phase.BACKFILL)
                    .findFirst()
                    .orElseThrow();
            assertTrue(backfill.sql().startsWith("UPDATE "), backfill.sql());
        }

        @Test
        @DisplayName("the temporary column is version-scoped so two attempts cannot collide")
        void temporaryColumnIsVersionScoped() {
            assertTrue(plan(narrow("amount", "VARCHAR(40)", "DOUBLE")).stream()
                    .anyMatch(s -> s.sql() != null && s.sql().contains("amount__v7")));
        }

        @Test
        @DisplayName("a new NOT NULL column is checked against the rows that already exist")
        void newNotNullColumnIsCheckedAgainstExistingRows() {
            // There is no old column to copy from, so expand-contract does not apply.
            // What matters is whether any row exists that would have no value.
            List<MigrationStep> p = plan(narrow("code", null, "VARCHAR(10)"));
            assertEquals(List.of(Phase.SNAPSHOT, Phase.PREFLIGHT, Phase.EXPAND), phases(p));
            assertTrue(p.get(1).dataCheck().contains("SELECT COUNT(*)"));
        }
    }

    @Nested
    @DisplayName("destructive")
    class Destructive {

        @Test
        @DisplayName("a drop is snapshotted first")
        void snapshotPrecedesTheDrop() {
            List<MigrationStep> p = plan(drop("legacy", "VARCHAR(10)"));
            assertEquals(Phase.SNAPSHOT, p.getFirst().phase());
            assertTrue(p.getFirst().sql().contains("sales__bak_7"));
        }

        @Test
        @DisplayName("the author is told how many rows hold a value, rather than being stopped")
        void dropReportsWhatWouldBeLost() {
            MigrationStep d = plan(drop("legacy", "VARCHAR(10)")).stream()
                    .filter(s -> s.phase() == Phase.CONTRACT)
                    .findFirst()
                    .orElseThrow();
            assertTrue(d.dataCheck().contains("`legacy` IS NOT NULL"));
            assertTrue(d.isDestructive());
        }

        @Test
        @DisplayName("one snapshot covers a whole plan, not one per change")
        void snapshotIsTakenOnce() {
            long snapshots = plan(drop("a", "INT"), drop("b", "INT"), narrow("c", "VARCHAR(9)", "INT")).stream()
                    .filter(s -> s.phase() == Phase.SNAPSHOT)
                    .count();
            assertEquals(1, snapshots);
        }
    }

    @Nested
    @DisplayName("re-runnability")
    class Reruns {

        @Test
        @DisplayName("every executable step is guarded, because MySQL has no ADD COLUMN IF NOT EXISTS")
        void everyStatementHasAPrecondition() {
            List<MigrationStep> p =
                    plan(widen("a", null, "INT"), narrow("amount", "VARCHAR(40)", "DOUBLE"), drop("old", "INT"));

            for (MigrationStep s : p) {
                if (s.sql() == null) continue;
                assertNotNull(s.precondition(), "unguarded step: " + s.sql());
                assertTrue(s.precondition().contains("information_schema"), s.precondition());
            }
        }

        @Test
        @DisplayName("an add is guarded on the column being absent, a change on it being present")
        void preconditionsMatchTheirStatements() {
            MigrationStep add = plan(widen("region", null, "VARCHAR(40)")).getFirst();
            assertTrue(add.precondition().contains("= 0"), "an ADD must be skipped if already applied");

            MigrationStep modify = plan(widen("code", "VARCHAR(10)", "VARCHAR(50)")).getFirst();
            assertTrue(!modify.precondition().contains("= 0"), "a MODIFY needs the column to exist");
        }

        @Test
        @DisplayName("the snapshot is skipped if a previous attempt already took it")
        void snapshotIsGuardedToo() {
            MigrationStep snap = plan(drop("a", "INT")).getFirst();
            assertTrue(snap.precondition().contains("information_schema.TABLES"));
            assertTrue(snap.precondition().contains("= 0"));
        }

        @Test
        void anEmptyChangeSetProducesNoPlan() {
            assertTrue(MySQLMigrationPlanner.plan(DB, T, 1, List.of()).isEmpty());
            assertTrue(MySQLMigrationPlanner.plan(DB, T, 1, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("ordering across a mixed plan")
    class Ordering {

        @Test
        @DisplayName("the snapshot is always first, before any statement that can lose data")
        void snapshotIsAlwaysFirst() {
            List<MigrationStep> p =
                    plan(widen("fresh", null, "INT"), drop("legacy", "INT"), narrow("amt", "VARCHAR(9)", "INT"));
            assertEquals(Phase.SNAPSHOT, p.getFirst().phase());
        }

        @Test
        @DisplayName("no CONTRACT step appears before the VERIFY that justifies it")
        void contractNeverPrecedesItsVerify() {
            List<MigrationStep> p = plan(narrow("amt", "VARCHAR(9)", "INT"));
            int verify = -1;
            for (int i = 0; i < p.size(); i++) {
                if (p.get(i).phase() == Phase.VERIFY || p.get(i).phase() == Phase.PREFLIGHT) verify = i;
                if (p.get(i).phase() == Phase.CONTRACT)
                    assertTrue(verify >= 0 && verify < i, "CONTRACT at " + i + " has no preceding VERIFY");
            }
        }
    }
}
