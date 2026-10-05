package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * How far one tenant got with one storage version, read back from its journal.
 *
 * A publish spans up to 71 independent schemas and any subset of them can stop part
 * way, so "did the publish work" is not a question with a yes or no answer. This is
 * the per-tenant answer that makes the question meaningful, and the input to deciding
 * whether re-running the publish is enough or someone has to look.
 *
 * @param database       the tenant schema
 * @param state          null when this tenant has never attempted this version
 * @param statementIndex the step it stopped on
 * @param total          steps in the plan, so the index means something
 * @param attempt        how many times it has been tried here
 */
public record TenantProgress(String database, MigrationState state, int statementIndex, int total, int attempt) {

    public boolean isDone() {
        return this.state == MigrationState.APPLIED;
    }

    /** Never started. Either the publish has not reached it, or it had nothing to do. */
    public boolean isUntouched() {
        return this.state == null;
    }

    /** Re-running the publish would carry this one forward. */
    public boolean isResumable() {
        return this.state == MigrationState.FAILED || this.state == MigrationState.RUNNING;
    }

    public String describe() {
        if (this.state == null) return this.database + ": not attempted";
        if (this.isDone()) return this.database + ": applied";
        return this.database + ": " + this.state + " at step " + this.statementIndex + " of " + this.total
                + " (attempt " + this.attempt + ")";
    }
}
