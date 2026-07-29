package com.modlix.saas.adzump.service.apply;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.AutonomyMode;
import com.modlix.saas.adzump.model.Money;

/**
 * J13 §5.1 — the typed, guardrail-relevant view of an {@link AutonomyConfig}'s
 * {@code campaignChanges} block (CONTRACT §3): the loop's {@link AutonomyMode mode} plus the caps the
 * {@link AutonomyRouter} routes on and the {@link GuardrailEngine} re-asserts at apply time.
 *
 * <p><b>Conservative by default.</b> {@link #from(AutonomyConfig)} falls back to {@link #conservative()}
 * (mode {@code RECOMMEND} — queue everything) whenever the config / body / {@code campaignChanges} block
 * is absent, so a mis-parsed or unset autonomy config can never widen what the loop may auto-apply. Each
 * cap is independently optional: a {@code <= 0} numeric cap (or {@code null} money cap) means
 * "unbounded / disabled" for that single guardrail, never "bypass all guardrails".
 *
 * @param mode                          RECOMMEND | HYBRID | AUTONOMOUS (§5.1 routing).
 * @param maxChangePerRunPct            max single-run budget swing as a <b>percentage</b> (e.g. {@code 20}
 *                                      = 20%); {@code <= 0} means unbounded. Parsed from
 *                                      {@code caps.maxBudgetChangePctPerRun}.
 * @param maxChangesPerRun              cap on the number of changes applied per run; {@code <= 0} unbounded.
 * @param dailyBudgetCap               absolute per-campaign daily budget ceiling; {@code null} disabled.
 * @param budgetCapFactor              relative ceiling as a multiple of the current daily budget (e.g.
 *                                      {@code 1.5} = never more than 150% of current); {@code <= 0} disabled.
 * @param doNoHarm                     never zero the only converter (re-asserted via {@link SignificanceGate}).
 * @param fastPauseSlowKill            a kill of a converter waits for {@code MATURE} CRM signal.
 * @param minHoursBetweenChangesPerEntity rate limit — the same entity may not be changed more often than
 *                                      this many hours; {@code <= 0} disabled.
 * @param staleSnapshotMaxAgeHours     an action built off a snapshot older than this (hours) is rejected,
 *                                      not applied; {@code <= 0} disabled.
 */
public record AutonomyPolicy(
        AutonomyMode mode,
        double maxChangePerRunPct,
        int maxChangesPerRun,
        Money dailyBudgetCap,
        double budgetCapFactor,
        boolean doNoHarm,
        boolean fastPauseSlowKill,
        long minHoursBetweenChangesPerEntity,
        long staleSnapshotMaxAgeHours) {

    /** The default max single-run budget swing (percent) used when caps are silent — conservative. */
    private static final double DEFAULT_MAX_CHANGE_PCT = 10.0d;

    /**
     * The conservative fallback: RECOMMEND mode (queue everything), a 10% single-run budget swing ceiling,
     * do-no-harm + fast-pause/slow-kill on, and every optional cap disabled. Used when no autonomy config
     * exists or its body cannot be read.
     */
    public static AutonomyPolicy conservative() {
        return new AutonomyPolicy(AutonomyMode.RECOMMEND, DEFAULT_MAX_CHANGE_PCT, 0, null, 0.0d,
                true, true, 0L, 0L);
    }

    /**
     * Parses the effective {@link AutonomyPolicy} from an {@link AutonomyConfig}. Reads
     * {@code body.campaignChanges.mode} and {@code body.campaignChanges.caps.*} (CONTRACT §3), defaulting
     * every unspecified knob to its {@link #conservative()} value. An absent config / body / block yields
     * {@link #conservative()} outright.
     */
    public static AutonomyPolicy from(AutonomyConfig config) {

        JsonNode body = config == null ? null : config.getBody();
        JsonNode campaignChanges = body == null ? null : body.get("campaignChanges");
        if (campaignChanges == null || !campaignChanges.isObject())
            return conservative();

        AutonomyMode mode = AutonomyMode.RECOMMEND;
        JsonNode modeNode = campaignChanges.get("mode");
        if (modeNode != null && modeNode.isTextual()) {
            AutonomyMode parsed = AutonomyMode.lookupLiteral(modeNode.asText());
            if (parsed != null)
                mode = parsed;
        }

        double maxChangePct = DEFAULT_MAX_CHANGE_PCT;
        int maxChanges = 0;
        Money dailyCap = null;
        double capFactor = 0.0d;
        boolean doNoHarm = true;
        boolean fastPauseSlowKill = true;
        long minHours = 0L;
        long staleHours = 0L;

        JsonNode caps = campaignChanges.get("caps");
        if (caps != null && caps.isObject()) {
            maxChangePct = caps.path("maxBudgetChangePctPerRun").asDouble(DEFAULT_MAX_CHANGE_PCT);
            maxChanges = caps.path("maxChangesPerRun").asInt(0);
            dailyCap = money(caps.get("dailyBudgetCap"));
            capFactor = caps.path("budgetCapFactor").asDouble(0.0d);
            doNoHarm = caps.path("doNoHarm").asBoolean(true);
            fastPauseSlowKill = caps.path("fastPauseSlowKill").asBoolean(true);
            minHours = caps.path("minHoursBetweenChangesPerEntity").asLong(0L);
            staleHours = caps.path("staleSnapshotMaxAgeHours").asLong(0L);
        }

        return new AutonomyPolicy(mode, maxChangePct, maxChanges, dailyCap, capFactor, doNoHarm,
                fastPauseSlowKill, minHours, staleHours);
    }

    /** Parses a {@code {"amount":N,"currency":"XXX"}} money node, or {@code null} when absent/malformed. */
    private static Money money(JsonNode node) {
        if (node == null || !node.isObject())
            return null;
        JsonNode amount = node.get("amount");
        if (amount == null || amount.isNull() || !amount.isNumber())
            return null;
        String currency = node.path("currency").asText(null);
        return new Money(BigDecimal.valueOf(amount.asDouble()), currency);
    }
}
