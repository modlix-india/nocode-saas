package com.fincity.saas.commons.core.service.connection.appdata.mysql;

/**
 * Where a storage's migration got to on one tenant.
 *
 * MySQL cannot roll a DDL statement back, so the journal is what turns a crash from
 * archaeology into a re-run. Written before the first statement and updated after each
 * one, it answers the only question that matters afterwards: how far did this get.
 */
public enum MigrationState {

    /** The plan exists and has been checked, but nothing has been executed. */
    PLANNED,

    /** At least one statement has been issued. A crash leaves the journal here. */
    RUNNING,

    /** Every statement in the plan succeeded. */
    APPLIED,

    /**
     * A statement failed. Recovery is to re-run the same plan: each statement is
     * guarded by a precondition, so the ones already applied are skipped.
     */
    FAILED,

    /**
     * A human has to look. Blocks further publishes of this storage, so a second
     * migration cannot be stacked on a broken first one.
     */
    NEEDS_ATTENTION,

    /**
     * Given up on deliberately, only ever from the expand phase, where abandoning
     * means dropping a spare column and the table is otherwise untouched.
     */
    ABANDONED
}
