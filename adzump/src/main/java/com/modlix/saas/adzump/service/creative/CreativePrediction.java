package com.modlix.saas.adzump.service.creative;

import java.util.List;

/**
 * The pre-spend predict-gate result A4 uses (J20 §5.3): a 0..1 expected-performance {@link #score()}
 * plus the {@link #confidence()} that says how much to trust it. A calibrated heuristic, <b>never an
 * LLM</b>.
 *
 * <p>{@link #coldStart()} true means the score leans on priors/vertical defaults rather than the
 * account's realized map, so A4 <b>soft-ranks</b> the creative rather than hard-blocking it — a weak
 * creative in a rich account is a confident drop; the same score with no data is a "needs a test"
 * (§5.3).
 *
 * @param score      0..1 expected performance (higher = better).
 * @param confidence 0..1 trust in the score (low when the draft's attributes have thin/absent evidence).
 * @param coldStart  whether the prediction is prior/default-seeded rather than realized-outcome-backed.
 * @param rationale  short per-axis notes (which attributes lifted or dragged the score), for A5/UX.
 */
public record CreativePrediction(
        double score,
        double confidence,
        boolean coldStart,
        List<String> rationale) {

    public CreativePrediction {
        rationale = rationale == null ? List.of() : List.copyOf(rationale);
    }
}
