package com.modlix.saas.adzump.service.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.Cadence;

/**
 * J14 §5.3 — the typed schedule view over an {@link AutonomyConfig}'s JSON body (the "ScheduleConfig
 * typed view over the same body" the design allows), mirroring
 * {@link com.modlix.saas.adzump.service.apply.AutonomyPolicy}. It reads the per-campaign optimization
 * {@link Cadence} the platform scheduler registers on. <b>Schema-free:</b> the cadence folds into the
 * same JSON body as the autonomy mode/caps — no new table, no new column.
 *
 * <p>Parses {@code body.schedule.optimizationCadence} first (mirroring the plan body's
 * {@link com.modlix.saas.adzump.model.ScheduleConfig#getOptimizationCadence()}), then a top-level
 * {@code body.optimizationCadence}. Defaults to {@link Cadence#ON_DEMAND} when absent/unparseable —
 * conservative: an unconfigured campaign is never auto-fired on a cadence, only run manually.
 */
public record SchedulePolicy(Cadence optimizationCadence) {

    /** The conservative default: no automatic cadence (manual "run now" only). */
    public static SchedulePolicy onDemand() {
        return new SchedulePolicy(Cadence.ON_DEMAND);
    }

    /**
     * Parses the effective {@link SchedulePolicy} from an {@link AutonomyConfig}. An absent config /
     * body / cadence yields {@link #onDemand()}.
     */
    public static SchedulePolicy from(AutonomyConfig config) {

        JsonNode body = config == null ? null : config.getBody();
        if (body == null || !body.isObject())
            return onDemand();

        Cadence cadence = readCadence(body.path("schedule").path("optimizationCadence"));
        if (cadence == null)
            cadence = readCadence(body.path("optimizationCadence"));

        return cadence == null ? onDemand() : new SchedulePolicy(cadence);
    }

    private static Cadence readCadence(JsonNode node) {
        if (node == null || !node.isTextual())
            return null;
        return Cadence.lookupLiteral(node.asText());
    }
}
