package com.modlix.saas.adzump.service.optimize;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict.GateOutcome;

/**
 * The load-bearing "never react to noise" gate (J12 §5.3). Every {@link Candidate} passes through
 * {@link #evaluate} before it can become a real {@link Action}. Four checks, in order of decisiveness:
 *
 * <ol>
 * <li><b>Minimum volume</b> — the grain must have enough impressions / clicks / outcomes for the
 * metric to mean something; below the policy's {@code gates.minVolumePerVariant} it is noise.</li>
 * <li><b>Do-no-harm</b> — never propose zeroing the only converter.</li>
 * <li><b>Signal maturity</b> — a pause/<b>kill of a converter</b> requires {@code MATURE} CRM signal
 * (fast signal proposes, slow signal disposes); on {@code FAST_ONLY}/{@code PARTIAL} data a kill of a
 * slow-converting potential winner is suppressed (top risk #2). Zero-outcome waste trims are
 * {@code kill == false} and are allowed on fast signal.</li>
 * <li><b>Statistical significance</b> — when the candidate carries a baseline comparison, the
 * difference vs the campaign baseline must clear a one-sided two-proportion test at the configured
 * confidence, not chance. Absolute-waste actions carry no baseline and are justified by volume
 * alone.</li>
 * </ol>
 *
 * Suppressed candidates return a typed {@link SignificanceVerdict} (reason + sample size) so a "no
 * action" is explainable. Deterministic Java; the thresholds come from the effective
 * {@link PerformancePolicy} gates and {@link AutonomyConfig} caps.
 */
@Service
public class SignificanceGate {

    /** Defaults used when the policy / autonomy config does not specify a knob. */
    static final long DEFAULT_MIN_VOLUME = 30L;
    static final double DEFAULT_CONFIDENCE = 0.90d;
    static final int DEFAULT_MAX_CHANGES = Integer.MAX_VALUE; // no cap in recommend-mode unless configured

    /**
     * The effective gate thresholds for a run.
     *
     * @param minVolume         min impressions/clicks/outcomes for a metric to be trustworthy.
     * @param confidence        the significance-test confidence (one-sided).
     * @param doNoHarm          never zero the only converter.
     * @param fastPauseSlowKill a kill of a converter waits for MATURE signal.
     * @param maxChangesPerRun  cap on the number of proposals per run (overflow is deferred, not dropped).
     */
    public record GateConfig(long minVolume, double confidence, boolean doNoHarm, boolean fastPauseSlowKill,
            int maxChangesPerRun) {
    }

    /**
     * Parses the effective gate config from the policy's {@code gates} block and the autonomy's
     * {@code campaignChanges.caps} block (CONTRACT §2/§3), falling back to conservative defaults.
     */
    public GateConfig config(PerformancePolicy policy, AutonomyConfig autonomy) {

        long minVolume = DEFAULT_MIN_VOLUME;
        double confidence = DEFAULT_CONFIDENCE;

        JsonNode gates = policy == null || policy.getBody() == null ? null : policy.getBody().get("gates");
        if (gates != null && gates.isObject()) {
            minVolume = gates.path("minVolumePerVariant").asLong(DEFAULT_MIN_VOLUME);
            confidence = gates.path("confidence").asDouble(DEFAULT_CONFIDENCE);
        }

        boolean doNoHarm = true;
        boolean fastPauseSlowKill = true;
        int maxChanges = DEFAULT_MAX_CHANGES;

        JsonNode caps = null;
        if (autonomy != null && autonomy.getBody() != null) {
            JsonNode campaignChanges = autonomy.getBody().get("campaignChanges");
            if (campaignChanges != null && campaignChanges.isObject())
                caps = campaignChanges.get("caps");
        }
        if (caps != null && caps.isObject()) {
            doNoHarm = caps.path("doNoHarm").asBoolean(true);
            fastPauseSlowKill = caps.path("fastPauseSlowKill").asBoolean(true);
            if (caps.has("maxChangesPerRun") && !caps.get("maxChangesPerRun").isNull())
                maxChanges = caps.path("maxChangesPerRun").asInt(DEFAULT_MAX_CHANGES);
        }

        return new GateConfig(minVolume, confidence, doNoHarm, fastPauseSlowKill, maxChanges);
    }

    /**
     * J13 apply-time re-assertion of the do-no-harm + maturity (+ min-volume) gates for a pause/kill,
     * exposed for the {@code service.apply} {@code GuardrailEngine} which lives outside this package and
     * therefore cannot build the package-private {@link Candidate}. Runs the exact same {@link #evaluate}
     * checks (2 volume / do-no-harm / maturity) against live state at apply time, so a pause that was
     * proposed on facts that have since changed is re-judged, not trusted (J13 §5.2). No baseline
     * comparison (the proportion test is skipped) — a pause is justified by volume + maturity + do-no-harm.
     */
    public SignificanceVerdict evaluatePauseGuardrail(boolean kill, boolean onlyConverter, SignalMaturity maturity,
            long volume, GateConfig cfg) {

        Candidate candidate = new Candidate(
                com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.PAUSE_ENTITY, null, null, null,
                0.0d, 0.0d, Risk.MED, kill, onlyConverter, maturity, volume, 0L, 0L, 0L, 0L);
        return this.evaluate(candidate, cfg);
    }

    /**
     * Judges one candidate. Returns a {@link SignificanceVerdict#passed()} verdict when it clears every
     * gate, else a typed suppression reason.
     */
    public SignificanceVerdict evaluate(Candidate c, GateConfig cfg) {

        // 1. Minimum volume — below this, the metric is noise.
        if (c.volume() < cfg.minVolume())
            return SignificanceVerdict.suppressed(GateOutcome.MIN_VOLUME, c.volume(), cfg.confidence(),
                    "volume " + c.volume() + " < min " + cfg.minVolume());

        // 2. Do-no-harm — never zero the only converter.
        if (cfg.doNoHarm() && c.kill() && c.onlyConverter())
            return SignificanceVerdict.suppressed(GateOutcome.DO_NO_HARM, c.volume(), cfg.confidence(),
                    "would zero the only converter");

        // 3. Signal maturity — a kill of a converter waits for MATURE (fast proposes, slow disposes).
        if (cfg.fastPauseSlowKill() && c.kill() && c.maturity() != SignalMaturity.MATURE)
            return SignificanceVerdict.suppressed(GateOutcome.IMMATURE_SIGNAL, c.volume(), cfg.confidence(),
                    "kill of a converter needs MATURE signal, was " + c.maturity());

        // 4. Statistical significance vs baseline (only when a baseline comparison is carried).
        if (c.baselineTrials() > 0) {
            double z = zScore(c.observedSuccesses(), c.observedTrials(), c.baselineSuccesses(), c.baselineTrials());
            double zCrit = zCritical(cfg.confidence());
            if (Math.abs(z) < zCrit)
                return SignificanceVerdict.suppressed(GateOutcome.NOT_SIGNIFICANT, c.observedTrials(),
                        cfg.confidence(),
                        "|z|=" + round(Math.abs(z)) + " < " + round(zCrit) + " (vs baseline)");
        }

        return SignificanceVerdict.passed(c.volume(), cfg.confidence(), "cleared volume/maturity/significance gates");
    }

    // ------------------------------------------------------------------------------------------
    // Two-proportion z-test (pooled), and the normal critical value for a confidence.
    // ------------------------------------------------------------------------------------------

    static double zScore(long x1, long n1, long x2, long n2) {
        if (n1 <= 0 || n2 <= 0)
            return 0.0d;
        double p1 = (double) x1 / n1;
        double p2 = (double) x2 / n2;
        double pPool = (double) (x1 + x2) / (n1 + n2);
        double se = Math.sqrt(pPool * (1.0d - pPool) * (1.0d / n1 + 1.0d / n2));
        return se > 0.0d ? (p1 - p2) / se : 0.0d;
    }

    /** One-sided normal critical value for common confidence levels (P3 lookup; ML calibration later). */
    static double zCritical(double confidence) {
        if (confidence >= 0.99d)
            return 2.3263d;
        if (confidence >= 0.975d)
            return 1.9600d;
        if (confidence >= 0.95d)
            return 1.6449d;
        if (confidence >= 0.90d)
            return 1.2816d;
        if (confidence >= 0.85d)
            return 1.0364d;
        if (confidence >= 0.80d)
            return 0.8416d;
        return 0.6745d;
    }

    private static double round(double v) {
        return Math.round(v * 100.0d) / 100.0d;
    }
}
