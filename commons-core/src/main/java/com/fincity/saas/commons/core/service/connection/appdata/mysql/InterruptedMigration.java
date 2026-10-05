package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

/**
 * A migration nobody is driving any more, read straight out of a tenant's journal.
 *
 * Everything needed to finish it is here, including the plan. Recovery resolves no
 * storage definitions and reads no Mongo: it runs when the rest of the system is in
 * a state nobody planned, so the less of that system it depends on, the better its
 * chances of working.
 */
public record InterruptedMigration(
        String database,
        String id,
        String storageName,
        String table,
        String shape,
        String surface,
        int statementIndex,
        int appliedCount,
        int attempt,
        int totalStatements,
        List<MigrationStep> plan) {

    /**
     * A row whose plan cannot be read back cannot be replayed.
     *
     * Rows written before the plan column existed look like this, and so does a row
     * whose JSON is damaged. Either way it is reported for a person rather than
     * guessed at.
     */
    public boolean isReplayable() {
        return this.plan != null && !this.plan.isEmpty();
    }

    /**
     * True when the interruption left the table in the one state that is actually
     * broken rather than merely untidy.
     *
     * Everything before CONTRACT leaves a working table carrying a spare column. Once
     * CONTRACT starts, the original column has been dropped and the temporary one is
     * not yet renamed, so reads and writes of that field fail until the rename runs.
     * It is one statement wide, and it is the reason the sweep runs at startup rather
     * than only on a timer.
     */
    public boolean isMidContract() {
        if (!this.isReplayable() || this.statementIndex >= this.plan.size()) return false;
        return this.plan.get(this.statementIndex).phase() == MigrationStep.Phase.CONTRACT;
    }

    public String describe() {
        return this.database + "/" + this.storageName + " step " + this.statementIndex + " of "
                + this.totalStatements + " (attempt " + this.attempt + ")"
                + (this.isMidContract() ? " MID-CONTRACT" : "");
    }
}
