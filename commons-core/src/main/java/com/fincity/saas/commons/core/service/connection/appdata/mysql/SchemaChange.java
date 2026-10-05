package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * One column-level difference between the storage definition and the table that
 * exists, classified by how dangerous it is to apply.
 */
public record SchemaChange(Kind kind, String column, String from, String to, String reason, String collation) {

    public SchemaChange(Kind kind, String column, String from, String to, String reason) {
        this(kind, column, from, to, reason, null);
    }

    /**
     * The {@code COLLATE} clause this change carries, or empty.
     *
     * Kept apart from {@code to} rather than folded into it because the data check
     * parses {@code to} as a type: a narrowing change would hand
     * {@code VARCHAR(20) COLLATE utf8mb4_bin} to the convertibility predicate, which
     * has no idea what to do with it.
     */
    public String collateClause() {
        return this.collation == null ? "" : " COLLATE " + this.collation;
    }

    /**
     * How much care applying this change needs.
     *
     * The classification is the whole point: most schema edits are harmless and should
     * apply without ceremony, and the few that are not must never apply without first
     * being checked against the rows that already exist.
     */
    public enum Kind {

        /** Safe against any existing data. Apply directly. */
        WIDENING,

        /**
         * May fail or lose data depending on what is already stored, so it needs a
         * counting query against live rows before the DDL, and expand-contract rather
         * than an in-place ALTER.
         */
        NARROWING,

        /** Loses a column outright. Needs the data check, a snapshot, and confirmation. */
        DESTRUCTIVE
    }

    public boolean needsDataCheck() {
        return this.kind != Kind.WIDENING;
    }
}
