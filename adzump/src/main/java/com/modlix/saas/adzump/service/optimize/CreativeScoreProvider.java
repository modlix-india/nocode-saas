package com.modlix.saas.adzump.service.optimize;

import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * The seam J12's {@link CreativeAnalyzer} consults for a creative's realized performance (J12 §5.2
 * creative "consult J20 creative scores"). J20 ({@code service.creative.CreativeScorer} +
 * {@code AttributeAttributor} + {@code CreativePredictor}) is the real owner — it reuses J10's
 * {@code PolicyScorer} at the creative grain, attributes outcomes to creative <b>attributes</b>, and
 * gates pre-spend with an ML predictor (never an LLM). J12 only needs the per-grain <b>signal</b>
 * (score + volume + maturity + winner flag) to decide rotate / pause / request-variant.
 *
 * <p><b>P3:</b> the default binding is {@link HeuristicCreativeScoreProvider}, which reads the signal
 * straight off the AD-grain snapshot row (its blended score + maturity). <b>TODO(J20):</b> replace the
 * default with the real J20 {@code CreativeScorer} once it lands, so the loop optimizes on
 * attribute-level quality, not just per-grain outcomes.
 */
public interface CreativeScoreProvider {

    /**
     * The creative signal for one AD-grain row.
     *
     * @param blendedScore the 0..100 realized creative score at this grain.
     * @param volume       the clicks (or impressions) the score rests on.
     * @param maturity     the CRM signal maturity (so a creative is not judged on thin data).
     * @param winner       whether this creative clears the winner bar for its account/vertical.
     */
    record CreativeSignal(double blendedScore, long volume, SignalMaturity maturity, boolean winner) {
    }

    /** The creative signal for the creative behind an AD-grain snapshot row, in the run's context. */
    CreativeSignal signalFor(SnapshotRow adRow, AnalyzerContext ctx);
}
