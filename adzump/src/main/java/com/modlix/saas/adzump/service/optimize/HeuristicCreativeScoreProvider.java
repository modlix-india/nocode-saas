package com.modlix.saas.adzump.service.optimize;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * The P3 default {@link CreativeScoreProvider}: reads the creative signal straight off the AD-grain
 * snapshot row — the row's blended {@code PolicyScorer} score, its click volume, and its CRM maturity —
 * with a fixed winner bar. It performs no attribute attribution and no pre-spend prediction; that is
 * J20's job.
 *
 * <p><b>TODO(J20):</b> supersede this bean with the real {@code service.creative.CreativeScorer}
 * (attribute-level lift + the ML predictor), so {@link CreativeAnalyzer} exploits winning
 * <i>attributes</i>, not just winning ad ids.
 */
@Service
public class HeuristicCreativeScoreProvider implements CreativeScoreProvider {

    /** A creative counts as a winner (worth requesting variants from) at/above this blended score. */
    static final double WINNER_SCORE = 70.0d;

    @Override
    public CreativeSignal signalFor(SnapshotRow adRow, AnalyzerContext ctx) {

        double score = adRow == null ? 0.0d : adRow.getBlendedScore();
        long volume = AnalyzerContext.clicks(adRow);
        SignalMaturity maturity = adRow == null || adRow.getSignalMaturity() == null
                ? SignalMaturity.FAST_ONLY
                : adRow.getSignalMaturity();

        boolean winner = score >= WINNER_SCORE && maturity == SignalMaturity.MATURE;

        return new CreativeSignal(score, volume, maturity, winner);
    }
}
