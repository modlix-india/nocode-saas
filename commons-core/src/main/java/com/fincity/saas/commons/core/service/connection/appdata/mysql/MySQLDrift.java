package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * What a live table has that its storage definition does not, and the other way round.
 *
 * Tables and definitions come apart. A migration is interrupted, an index is added by
 * hand to chase a slow page, a column outlives the field that created it, an app is
 * transported into a tenant whose table was built from an older shape. Nothing
 * noticed, because every other code path asks the DEFINITION what the table looks
 * like and the table is only consulted when a query fails.
 *
 * Reporting and repairing are deliberately separate calls. A reconciler that repairs
 * as it reports is one nobody can safely run to find out how bad things are, and the
 * only honest way to describe a DROP COLUMN is before it happens rather than after.
 */
public final class MySQLDrift {

    private MySQLDrift() {}

    /**
     * Everything that differs, classified.
     *
     * Column differences arrive already graded by {@link SchemaChange.Kind}, which is
     * what the approval gate keys on: WIDENING is safe, NARROWING may not fit the rows
     * that exist, DESTRUCTIVE loses a column outright.
     */
    public record Report(
            String db,
            String table,
            boolean tableExists,
            List<SchemaChange> columns,
            List<String> indexStatements,
            List<String> foreignKeyStatements) {

        @JsonProperty("clean")
        public boolean clean() {
            return this.tableExists
                    && this.columns.isEmpty()
                    && this.indexStatements.isEmpty()
                    && this.foreignKeyStatements.isEmpty();
        }

        /** The column changes that must never run unasked. */
        @JsonProperty("dangerous")
        public List<SchemaChange> dangerous() {
            return this.columns.stream().filter(SchemaChange::needsDataCheck).toList();
        }

        /**
         * Index and key DROPs. Reversible - the definition can rebuild them - but still
         * a change to a live table that somebody should agree to.
         */
        @JsonProperty("drops")
        public List<String> drops() {
            List<String> out = new ArrayList<>();
            for (String s : this.indexStatements) if (isDrop(s)) out.add(s);
            for (String s : this.foreignKeyStatements) if (isDrop(s)) out.add(s);
            return out;
        }

        /** True when repairing this would do something irreversible or lossy. */
        @JsonProperty("needsApproval")
        public boolean needsApproval() {
            return !this.dangerous().isEmpty() || !this.drops().isEmpty() || !this.tableExists;
        }

        @JsonProperty("summary")
        public String summary() {
            if (this.clean()) return this.db + "." + this.table + ": in sync";
            if (!this.tableExists) return this.db + "." + this.table + ": table is MISSING";
            return this.db + "." + this.table + ": " + this.columns.size() + " column change(s) ("
                    + this.dangerous().size() + " needing approval), " + this.indexStatements.size()
                    + " index statement(s), " + this.foreignKeyStatements.size() + " key statement(s)";
        }
    }

    /**
     * What a repair actually did on one tenant.
     *
     * {@code withheld} is the point of the record. A repair run without approval is
     * supposed to leave the dangerous half alone, and the caller has to be able to
     * see WHAT it left rather than infer it from a count - that list is the argument
     * for granting approval, and the thing to read before granting it.
     */
    public record DriftRepair(
            String db, boolean tableMissing, List<String> applied, List<String> withheld, List<String> failed) {

        /**
         * A table that is not there is not repaired, and must not read as a clean
         * run. Nothing here creates one - that is {@code ensureTable}'s job and it
         * needs the whole definition, not a diff against nothing - so the honest
         * answer is to carry the fact out and let the caller act on it.
         */
        @JsonProperty("complete")
        public boolean complete() {
            return !this.tableMissing && this.failed.isEmpty() && this.withheld.isEmpty();
        }
    }

    /**
     * The statements an approved repair would run that this one will not.
     *
     * Computed as the difference rather than tracked separately, so it cannot drift
     * from what {@link #repairStatements} actually withholds.
     */
    public static List<String> withheldStatements(Report report, boolean approved) {

        if (approved) return List.of();

        List<String> full = repairStatements(report, true);
        List<String> safe = repairStatements(report, false);

        List<String> out = new ArrayList<>(full);
        out.removeAll(safe);
        return out;
    }

    private static boolean isDrop(String statement) {
        String s = statement.toUpperCase();
        return s.startsWith("DROP INDEX") || s.contains("DROP FOREIGN KEY") || s.contains("DROP COLUMN");
    }

    /**
     * Build a report from what was inspected and what the definition wants.
     *
     * Takes the already-resolved pieces rather than fetching them, so the whole
     * classification is testable without a database - which is the part worth
     * testing, since the fetching is just three queries.
     */
    public static Report of(
            String db,
            String table,
            boolean tableExists,
            List<MySQLColumn> existingColumns,
            List<MySQLColumn> desiredColumns,
            List<String> indexStatements,
            List<String> foreignKeyStatements) {

        if (!tableExists)
            return new Report(db, table, false, List.of(), List.of(), List.of());

        return new Report(
                db,
                table,
                true,
                MySQLTablePlanner.diff(existingColumns, desiredColumns),
                indexStatements == null ? List.of() : indexStatements,
                foreignKeyStatements == null ? List.of() : foreignKeyStatements);
    }

    /**
     * The DDL that would bring the table back to its definition.
     *
     * Without approval this returns ONLY the safe half: widening column changes and
     * additive index/key work. Everything that drops or narrows is withheld, so an
     * unapproved repair can be run on anything without reading the report first and
     * still cannot lose a column, a key or a row.
     *
     * The order is the whole difficulty, and it is not one order but two pulling
     * opposite ways. A key has to come OFF before the index backing it can be
     * dropped, and a column has to be THERE before an index or a key can be put on
     * it. Emitting keys and indexes as one block ahead of the columns - which is what
     * this did - satisfies the first and breaks the second: adding a field and an
     * index on it in the same edit planned the index first and failed on a column
     * that did not exist yet.
     *
     * So the plan runs outside in: everything that comes off, then the columns, then
     * everything that goes back on.
     */
    public static List<String> repairStatements(Report report, boolean approved) {

        if (report == null || report.clean()) return List.of();

        List<String> out = new ArrayList<>();

        // Off first, keys before the indexes they sit on.
        if (approved) {
            for (String s : report.foreignKeyStatements()) if (isDrop(s)) out.add(s);
            for (String s : report.indexStatements()) if (isDrop(s)) out.add(s);
        }

        for (SchemaChange change : report.columns()) {
            if (change.needsDataCheck() && !approved) continue;
            out.add(columnDdl(report.db(), report.table(), change));
        }

        // Back on, indexes before the keys that need them.
        for (String s : report.indexStatements()) if (!isDrop(s)) out.add(s);
        for (String s : report.foreignKeyStatements()) if (!isDrop(s)) out.add(s);

        return out;
    }

    private static String columnDdl(String db, String table, SchemaChange change) {

        String target = "`" + db + "`.`" + table + "`";

        if (change.kind() == SchemaChange.Kind.DESTRUCTIVE && change.to() == null)
            return "ALTER TABLE " + target + " DROP COLUMN `" + change.column() + "`";

        if (change.from() == null)
            return "ALTER TABLE " + target + " ADD COLUMN `" + change.column() + "` " + change.to()
                    + change.collateClause();

        return "ALTER TABLE " + target + " MODIFY COLUMN `" + change.column() + "` " + change.to()
                + change.collateClause();
    }
}
