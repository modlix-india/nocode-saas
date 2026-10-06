package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * One statement in a migration plan, with the question that decides whether it may run.
 *
 * @param phase      which part of expand-contract this belongs to
 * @param sql        the statement itself
 * @param precondition a query returning a count; the step is skipped when it returns 0.
 *                   This is what makes re-running a plan the recovery path, because
 *                   MySQL 8 has no ADD COLUMN IF NOT EXISTS and a blind re-run would
 *                   fail on the statements already applied.
 * @param dataCheck  a query counting rows that would NOT survive this step. Non-zero
 *                   stops the migration. Null when the step cannot lose data.
 * @param describes  the schema change this step serves, for the journal and for
 *                   telling an author what is about to happen
 */
public record MigrationStep(Phase phase, String sql, String precondition, String dataCheck, SchemaChange describes) {

    /**
     * Expand-contract, so nothing destructive happens until the data is proven to
     * survive it.
     *
     * The ordering is the recovery story: a failure anywhere before CONTRACT leaves a
     * working table with at most a spare column, which is a cleanup task rather than an
     * incident. Only CONTRACT can lose anything, and by then VERIFY has passed.
     */
    public enum Phase {
        /**
         * The gate, read against the ORIGINAL column only, so it can run across every
         * tenant before a single statement is issued anywhere. Distinct from VERIFY
         * precisely because both are checks with no SQL of their own: telling them
         * apart by shape would mean trying to pre-flight a check that needs a column
         * the migration has not created yet.
         */
        PREFLIGHT,

        /** Add the new column alongside the old. Nothing is lost if this fails. */
        EXPAND,

        /** Copy data across. DML, so transactional and genuinely rollback-able. */
        BACKFILL,

        /** Mid-plan re-check once the backfill has run. Non-zero stops everything. */
        VERIFY,

        /** Copy the table before anything destructive. Cheap at these data sizes. */
        SNAPSHOT,

        /** Drop or rename. The only phase that can lose data. */
        CONTRACT
    }

    public boolean isDestructive() {
        return this.phase == Phase.CONTRACT;
    }
}
