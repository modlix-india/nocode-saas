package com.modlix.saas.adzump.service.optimize;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

/**
 * A pre-gate proposal from a {@link DimensionAnalyzer}: everything an {@link Action} needs plus the
 * statistical context the {@link SignificanceGate} judges it on. Analyzers propose <b>liberally</b>
 * (the diagnosis); the gate disposes (the "is it real?" decision is centralised, not duplicated per
 * analyzer). Package-private — never leaves the engine.
 *
 * @param type          the action type.
 * @param target        the ad-grain the change is aimed at.
 * @param change        the typed change payload.
 * @param rationale     the human-readable "why".
 * @param expectedDelta the estimated blended-objective gain in points (heuristic in P3).
 * @param confidence    the analyzer's own confidence in the diagnosis (0..1).
 * @param risk          the blast radius.
 * @param kill          {@code true} when this pauses/kills a <b>converter</b> (a grain that has
 *                      produced CRM outcomes) — such kills require MATURE signal.
 * @param onlyConverter {@code true} when the target is the only grain still producing outcomes
 *                      (do-no-harm never zeroes it).
 * @param maturity      the target row's CRM signal maturity.
 * @param volume        the relevant volume the min-volume gate checks (clicks for conversion-based
 *                      actions, impressions for CTR-based ones, outcomes where that is the metric).
 * @param observedTrials the target grain's trials for the two-proportion significance test (e.g.
 *                       clicks); {@code 0} when this action has no baseline comparison (absolute-waste
 *                       actions), in which case the gate skips the proportion test.
 * @param observedSuccesses the target grain's successes (e.g. outcomes) for the significance test.
 * @param baselineTrials    the baseline (campaign / sibling pool) trials; {@code 0} disables the test.
 * @param baselineSuccesses the baseline successes for the test.
 */
record Candidate(
        AdzumpActionAuditActionType type,
        AdGrainId target,
        ActionChange change,
        String rationale,
        double expectedDelta,
        double confidence,
        Risk risk,
        boolean kill,
        boolean onlyConverter,
        SignalMaturity maturity,
        long volume,
        long observedTrials,
        long observedSuccesses,
        long baselineTrials,
        long baselineSuccesses) {
}
