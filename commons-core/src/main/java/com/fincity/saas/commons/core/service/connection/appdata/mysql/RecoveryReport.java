package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;
import java.util.Map;

/**
 * What one sweep did.
 *
 * Reported rather than logged and forgotten, because the question after a bad deploy
 * is "did anything get left behind", and a recovery that cannot answer it is only
 * half of one.
 *
 * @param found     interrupted rows seen across every tenant
 * @param claimed   rows this node won and attempted; the rest belong to another node
 * @param resumed   rows that ran through to APPLIED
 * @param outcomes  per row, for the ones this node attempted
 * @param unplayable rows whose plan could not be read back, which need a person
 */
public record RecoveryReport(
        int found,
        int claimed,
        int resumed,
        Map<String, MigrationOutcome> outcomes,
        List<String> unplayable) {

    public static RecoveryReport empty() {
        return new RecoveryReport(0, 0, 0, Map.of(), List.of());
    }

    public boolean isQuiet() {
        return this.found == 0;
    }

    /** One line for a log that is read after something went wrong. */
    public String summary() {
        if (this.found == 0) return "no interrupted migrations";

        return this.found + " interrupted, " + this.claimed + " claimed by this node, " + this.resumed
                + " finished" + (this.unplayable.isEmpty() ? "" : ", " + this.unplayable.size()
                        + " with no replayable plan: " + this.unplayable);
    }
}
