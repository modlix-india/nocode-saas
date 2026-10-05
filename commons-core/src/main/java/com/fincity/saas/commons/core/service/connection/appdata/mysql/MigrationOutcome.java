package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * What happened to one tenant's migration.
 *
 * A publish touches many tenants independently, so the caller needs per-tenant detail
 * rather than a boolean: partial completion across a fan-out is a normal state, not an
 * error.
 *
 * @param state       where the journal ended up
 * @param applied     statements actually executed on THIS attempt, excluding those a
 *                    precondition skipped
 * @param skipped     statements a previous attempt had already applied
 * @param resumedFrom the step a retry picked up from, or -1 when this was the first
 *                    attempt. Worth reporting rather than hiding: "migrated" and
 *                    "finished a migration that had failed half way" are the same
 *                    outcome but very different things to read in a publish report
 * @param blockedBy   the data check that stopped it, or null
 * @param offending   rows that would not survive that check
 * @param error       the failure message, or null
 */
public record MigrationOutcome(
        MigrationState state,
        int applied,
        int skipped,
        int resumedFrom,
        String blockedBy,
        long offending,
        String error) {

    public boolean isSuccess() {
        return this.state == MigrationState.APPLIED;
    }

    public boolean wasResumed() {
        return this.resumedFrom >= 0;
    }

    static MigrationOutcome applied(int applied, int skipped) {
        return new MigrationOutcome(MigrationState.APPLIED, applied, skipped, -1, null, 0, null);
    }

    static MigrationOutcome blocked(String check, long offending, int applied, int skipped) {
        return new MigrationOutcome(
                MigrationState.FAILED,
                applied,
                skipped,
                -1,
                check,
                offending,
                offending + " row(s) would not survive this change");
    }

    static MigrationOutcome failed(String error, int applied, int skipped) {
        return new MigrationOutcome(MigrationState.FAILED, applied, skipped, -1, null, 0, error);
    }

    /**
     * Another publish holds this migration, so this one did nothing.
     *
     * RUNNING rather than APPLIED or FAILED, because neither is true: the work is
     * happening, just not here. Reporting it applied would claim a table is at a
     * shape this node never checked, and reporting it failed would block a publish
     * that is in fact proceeding.
     */
    public static MigrationOutcome inProgressElsewhere(String note) {
        return new MigrationOutcome(MigrationState.RUNNING, 0, 0, -1, null, 0, note);
    }

    public static MigrationOutcome needsAttention(String error) {
        return new MigrationOutcome(MigrationState.NEEDS_ATTENTION, 0, 0, -1, null, 0, error);
    }

    /** The same outcome, tagged with where the retry picked up. */
    MigrationOutcome resumedFrom(int index) {
        return new MigrationOutcome(
                this.state, this.applied, this.skipped, index, this.blockedBy, this.offending, this.error);
    }
}
