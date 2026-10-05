package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.MigrationStep.Phase;

/**
 * Turns classified schema changes into an ordered, re-runnable plan.
 *
 * Every step carries a precondition, because MySQL 8 has no ADD COLUMN IF NOT EXISTS
 * and recovery from a half-applied migration is to re-run the same plan rather than
 * unwind it. Every lossy step carries a counting query that must return zero first.
 *
 * Pure: the SQL is generated here and executed elsewhere, so the whole plan for any
 * change can be asserted as text without a database.
 */
public final class MySQLMigrationPlanner {

    /** Suffix for the column added during EXPAND, before CONTRACT renames it into place. */
    static final String NEW_SUFFIX = "__v";

    private MySQLMigrationPlanner() {
    }

    public static List<MigrationStep> plan(String db, String table, int toVersion, List<SchemaChange> changes) {

        List<MigrationStep> steps = new ArrayList<>();
        if (changes == null || changes.isEmpty()) return steps;

        boolean anyDestructive = changes.stream().anyMatch(c -> c.kind() == SchemaChange.Kind.DESTRUCTIVE
                || c.kind() == SchemaChange.Kind.NARROWING);

        // One snapshot for the whole plan, before anything that could lose data. At a
        // 40MB ceiling per tenant this costs nothing and is the simplest possible undo.
        if (anyDestructive) steps.add(snapshot(db, table, toVersion));

        for (SchemaChange c : changes) {
            switch (c.kind()) {
                case WIDENING -> steps.addAll(widening(db, table, c));
                case NARROWING -> steps.addAll(narrowing(db, table, toVersion, c));
                case DESTRUCTIVE -> steps.addAll(destructive(db, table, c));
            }
        }

        return steps;
    }

    private static MigrationStep snapshot(String db, String table, int toVersion) {
        String bak = table + "__bak_" + toVersion;
        return new MigrationStep(
                Phase.SNAPSHOT,
                "CREATE TABLE `" + db + "`.`" + bak + "` AS SELECT * FROM `" + db + "`.`" + table + "`",
                tableAbsent(db, bak),
                null,
                null);
    }

    /** Safe by classification, so one statement and no data check. */
    private static List<MigrationStep> widening(String db, String table, SchemaChange c) {

        if (c.from() == null)
            return List.of(new MigrationStep(
                    Phase.EXPAND,
                    "ALTER TABLE `" + db + "`.`" + table + "` ADD COLUMN `" + c.column() + "` " + c.to() + c.collateClause() + " NULL",
                    columnAbsent(db, table, c.column()),
                    null,
                    c));

        return List.of(new MigrationStep(
                Phase.EXPAND,
                "ALTER TABLE `" + db + "`.`" + table + "` MODIFY COLUMN `" + c.column() + "` " + c.to() + c.collateClause(),
                columnPresent(db, table, c.column()),
                null,
                c));
    }

    /**
     * Expand-contract. The old column survives until VERIFY has proven every row
     * converts, so a failure at any earlier point is recoverable by dropping a spare
     * column.
     */
    private static List<MigrationStep> narrowing(String db, String table, int toVersion, SchemaChange c) {

        // A brand new NOT NULL column cannot be expand-contracted: there is no old
        // column to copy from. It is added nullable and the check is on the rows that
        // already exist.
        if (c.from() == null)
            return List.of(
                    new MigrationStep(
                            Phase.PREFLIGHT,
                            null,
                            null,
                            "SELECT COUNT(*) FROM `" + db + "`.`" + table + "`",
                            c),
                    new MigrationStep(
                            Phase.EXPAND,
                            "ALTER TABLE `" + db + "`.`" + table + "` ADD COLUMN `" + c.column() + "` " + c.to() + c.collateClause()
                                    + " NOT NULL",
                            columnAbsent(db, table, c.column()),
                            null,
                            c));

        String tmp = c.column() + NEW_SUFFIX + toVersion;
        String q = "`" + db + "`.`" + table + "`";
        String convertible = MySQLConvertibility.predicate(c.column(), c.to());

        List<MigrationStep> steps = new ArrayList<>();

        // The gate, and it comes FIRST. Expressed purely over the original column, so
        // it can be run against every tenant before any of them is touched. A check
        // that referenced the temporary column could only run mid-migration, by which
        // point the decision to start has already been taken.
        steps.add(new MigrationStep(
                Phase.PREFLIGHT, null, null, MySQLConvertibility.preflight(db, table, c.column(), c.to()), c));

        steps.add(new MigrationStep(
                Phase.EXPAND,
                "ALTER TABLE " + q + " ADD COLUMN `" + tmp + "` " + c.to() + c.collateClause() + " NULL",
                columnAbsent(db, table, tmp),
                null,
                c));

        // Only the rows that will convert. MySQL in strict mode aborts the whole
        // statement on a value it cannot cast rather than writing NULL, so copying
        // blindly dies on the first bad row instead of leaving it to be counted.
        steps.add(new MigrationStep(
                Phase.BACKFILL,
                "UPDATE " + q + " SET `" + tmp + "` = `" + c.column() + "` WHERE `" + c.column()
                        + "` IS NOT NULL AND (" + convertible + ")",
                columnPresent(db, table, tmp),
                null,
                c));

        // Belt and braces: anything the predicate let through but MySQL still refused
        // shows up as a null that was not null before.
        steps.add(new MigrationStep(
                Phase.VERIFY,
                null,
                null,
                "SELECT COUNT(*) FROM " + q + " WHERE `" + c.column() + "` IS NOT NULL AND `" + tmp + "` IS NULL",
                c));

        steps.add(new MigrationStep(
                Phase.CONTRACT,
                "ALTER TABLE " + q + " DROP COLUMN `" + c.column() + "`",
                columnPresent(db, table, c.column()),
                null,
                c));

        steps.add(new MigrationStep(
                Phase.CONTRACT,
                "ALTER TABLE " + q + " CHANGE COLUMN `" + tmp + "` `" + c.column() + "` " + c.to() + c.collateClause(),
                columnPresent(db, table, tmp),
                null,
                c));

        return steps;
    }

    private static List<MigrationStep> destructive(String db, String table, SchemaChange c) {
        return List.of(new MigrationStep(
                Phase.CONTRACT,
                "ALTER TABLE `" + db + "`.`" + table + "` DROP COLUMN `" + c.column() + "`",
                columnPresent(db, table, c.column()),
                // Blocks when the column holds anything. There is no confirmation
                // mechanism yet, and refusing by default is the right side to err on:
                // the snapshot above makes the drop recoverable, not harmless.
                "SELECT COUNT(*) FROM `" + db + "`.`" + table + "` WHERE `" + c.column() + "` IS NOT NULL",
                c));
    }

    // ---- preconditions, all against information_schema ----

    static String columnAbsent(String db, String table, String column) {
        return "SELECT COUNT(*) = 0 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + db
                + "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
    }

    static String columnPresent(String db, String table, String column) {
        return "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + db
                + "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
    }

    static String tableAbsent(String db, String table) {
        return "SELECT COUNT(*) = 0 FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + db
                + "' AND TABLE_NAME = '" + table + "'";
    }
}
