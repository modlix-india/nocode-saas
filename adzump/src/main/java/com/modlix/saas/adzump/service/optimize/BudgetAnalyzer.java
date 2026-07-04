package com.modlix.saas.adzump.service.optimize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Budget dimension (J12 §5.2): the classic reallocation — shift spend from the lowest-{@code
 * blendedScore} grain to the highest, within caps. Ported from the legacy budget logic as
 * deterministic Java, with the J10 discipline the legacy lacked: it proposes only when the score gap
 * is material and never shifts off the only converter; the {@link SignificanceGate} then confirms the
 * winner/loser rate difference is real (not chance) and that both grains cleared min-volume.
 *
 * <p>Operates at the ad-set / ad-group grain (where budget is allocated). The shift is bounded to
 * {@link #DEFAULT_MAX_SHIFT_PCT} of the loser's window spend; <b>TODO</b> source the per-account cap
 * from {@code AutonomyConfig.caps.maxBudgetChangePctPerRun} (J13 enforces it at apply-time).
 */
@Service
public class BudgetAnalyzer implements DimensionAnalyzer {

    /** Only reallocate when the winner outscores the loser by at least this many objective points. */
    static final double MIN_SCORE_GAP = 10.0d;

    /** The fraction of the loser's window spend moved per run (the default cap). */
    static final double DEFAULT_MAX_SHIFT_PCT = 0.20d;

    private static final int MONEY_SCALE = 2;

    @Override
    public List<Candidate> analyze(AnalyzerContext ctx) {

        List<Candidate> out = new ArrayList<>();
        Grain grain = ctx.operatingGrain();
        List<SnapshotRow> rows = ctx.rowsAt(grain);
        if (rows.size() < 2)
            return out; // nothing to reallocate between

        SnapshotRow winner = null;
        SnapshotRow loser = null;
        for (SnapshotRow r : rows) {
            if (winner == null || r.getBlendedScore() > winner.getBlendedScore())
                winner = r;
            if (loser == null || r.getBlendedScore() < loser.getBlendedScore())
                loser = r;
        }
        if (winner == null || loser == null || winner == loser)
            return out;

        double gap = winner.getBlendedScore() - loser.getBlendedScore();
        if (gap < MIN_SCORE_GAP)
            return out;

        // Do-no-harm at the analyzer level: never propose shifting off the only converter.
        if (AnalyzerContext.isConverter(loser) && ctx.converterCountAt(grain) <= 1)
            return out;

        double loserSpend = AnalyzerContext.spend(loser);
        if (loserSpend <= 0.0d)
            return out;

        double campaignSpend = ctx.campaignSpend();
        double shiftAmount = round(loserSpend * DEFAULT_MAX_SHIFT_PCT);
        double shiftFraction = campaignSpend > 0.0d ? shiftAmount / campaignSpend : 0.0d;

        // Moving spend from a score-sL grain to a score-sW grain lifts the spend-weighted rollup by
        // roughly the score gap times the reallocated share of total spend (conservative P3 heuristic).
        double expectedDelta = gap * shiftFraction;

        String currency = AnalyzerContext.currency(loser);
        Money amount = new Money(BigDecimal.valueOf(shiftAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP), currency);

        ActionChange change = new ActionChange.BudgetShift(loser.getAdGrainId(), winner.getAdGrainId(), amount,
                DEFAULT_MAX_SHIFT_PCT);

        String rationale = String.format(
                "Shift %s%% of spend off the grain scoring %.1f onto the grain scoring %.1f (gap %.1f).",
                (int) (DEFAULT_MAX_SHIFT_PCT * 100), loser.getBlendedScore(), winner.getBlendedScore(), gap);

        long loserSuccess = success(loser, ctx);
        long loserTrials = AnalyzerContext.clicks(loser);
        long winnerSuccess = success(winner, ctx);
        long winnerTrials = AnalyzerContext.clicks(winner);

        out.add(new Candidate(
                AdzumpActionAuditActionType.SHIFT_BUDGET,
                loser.getAdGrainId(),
                change,
                rationale,
                expectedDelta,
                0.80d,
                Risk.MED,
                false, // budget reallocation is not a kill
                false,
                loser.getSignalMaturity(),
                loserTrials,       // min-volume applies to the grain we reallocate away from
                loserTrials,
                loserSuccess,
                winnerTrials,      // baseline for the significance test = the winner grain
                winnerSuccess));

        return out;
    }

    /** The success metric for the rate test: entry-stage leads, else any CRM outcome, else fast conversions. */
    static long success(SnapshotRow row, AnalyzerContext ctx) {
        long s = 0L;
        if (ctx.entryStage() != null)
            s = AnalyzerContext.milestoneCount(row, ctx.entryStage());
        if (s == 0L)
            s = AnalyzerContext.totalOutcomes(row);
        if (s == 0L)
            s = AnalyzerContext.platformConversions(row);
        return s;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
