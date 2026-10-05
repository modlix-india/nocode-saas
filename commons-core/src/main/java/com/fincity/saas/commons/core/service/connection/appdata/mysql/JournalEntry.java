package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * The journal's answer to "how far did this get", for one storage version on one
 * tenant.
 *
 * @param id             the journal row, which a retry continues rather than replaces
 * @param state          where it stopped
 * @param statementIndex the step last attempted. Progress is written BEFORE the
 *                       statement, so this step may or may not have run, and a resume
 *                       re-attempts it rather than stepping over it
 * @param attempt        how many times this migration has been tried here
 * @param appliedCount   statements executed across every attempt, so a plan that
 *                       landed in three goes still reports what it actually did
 * @param total          steps in the plan, so the index means something to a reader
 */
public record JournalEntry(
        String id, MigrationState state, int statementIndex, int attempt, int appliedCount, int total) {

    /** Finished. Nothing to do, and the plan must not be replayed. */
    public boolean isComplete() {
        return this.state == MigrationState.APPLIED;
    }

    /**
     * Stopped part way and safe to continue.
     *
     * NEEDS_ATTENTION is excluded on purpose: it is only ever set by a human and it
     * means a human has to look. ABANDONED is excluded too, but for the opposite
     * reason: it has been written off, so the next publish starts clean.
     */
    public boolean isResumable() {
        return this.state == MigrationState.FAILED || this.state == MigrationState.RUNNING;
    }
}
