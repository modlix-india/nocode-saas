package com.modlix.saas.adzump.service.optimize;

/**
 * The output of the {@link SignificanceGate} for one candidate action (J12 §5.3) — the load-bearing
 * "is this signal real, or noise?" decision. A candidate becomes a real {@link Action} only when the
 * verdict {@link #passed()}; otherwise it is suppressed with a machine-readable {@link #outcome()} and
 * a human-readable {@link #detail()} so a "no action" is <b>explainable</b>, never silent.
 *
 * @param outcome    why the gate decided as it did ({@link GateOutcome#PASSED} = proposal survives).
 * @param sampleSize the volume the decision was made on (impressions / clicks / outcomes as relevant),
 *                   surfaced so the reason is auditable.
 * @param confidence the confidence the decision was taken at (the configured gate confidence when it
 *                   passed a significance test; the target confidence otherwise).
 * @param detail     a short human-readable explanation for the rail / audit.
 */
public record SignificanceVerdict(GateOutcome outcome, double sampleSize, double confidence, String detail) {

    /**
     * Why the gate accepted or suppressed a candidate.
     */
    public enum GateOutcome {
        /** Cleared every gate — becomes a real proposal. */
        PASSED,
        /** Below the minimum volume for the metric to mean anything (impressions / clicks / outcomes). */
        MIN_VOLUME,
        /** The difference vs the campaign baseline did not clear the significance test (could be chance). */
        NOT_SIGNIFICANT,
        /** A pause/kill of a converter on immature CRM signal (fast-only / partial) — kills wait for MATURE. */
        IMMATURE_SIGNAL,
        /** Do-no-harm: it would zero the only converter. */
        DO_NO_HARM,
        /** Ranked out by the max-changes-per-run cap (a real proposal, deferred to the next run). */
        MAX_CHANGES_EXCEEDED
    }

    public boolean passed() {
        return this.outcome == GateOutcome.PASSED;
    }

    public static SignificanceVerdict passed(double sampleSize, double confidence, String detail) {
        return new SignificanceVerdict(GateOutcome.PASSED, sampleSize, confidence, detail);
    }

    public static SignificanceVerdict suppressed(GateOutcome outcome, double sampleSize, double confidence,
            String detail) {
        return new SignificanceVerdict(outcome, sampleSize, confidence, detail);
    }
}
