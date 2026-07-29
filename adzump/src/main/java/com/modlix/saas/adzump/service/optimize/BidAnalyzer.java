package com.modlix.saas.adzump.service.optimize;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Bid dimension (J12 §5.2): raise or lower a grain's target cost-per-outcome against the campaign
 * target. Ported from the legacy cost-per-conversion logic — <b>lower</b> the bid on a grain paying
 * materially over target, <b>raise</b> it on a strong grain converting well under target (to capture
 * more of the cheap volume). Only acts on grains with a computable, stable cost (enough outcomes), and
 * leans on min-volume so it never re-bids on noise; the target comes from the plan objective's
 * {@code targetCostPerOutcome}.
 */
@Service
public class BidAnalyzer implements DimensionAnalyzer {

    /** Over/under the target cost-per-outcome by this ratio before a bid change is worth proposing. */
    static final double OVER_RATIO = 1.30d;
    static final double UNDER_RATIO = 0.70d;

    /** A cost-per-outcome needs at least this many outcomes to be stable enough to re-bid on. */
    static final long MIN_OUTCOMES_FOR_BID = 3L;

    @Override
    public List<Candidate> analyze(AnalyzerContext ctx) {

        List<Candidate> out = new ArrayList<>();

        Money target = targetCostPerOutcome(ctx);
        if (target == null || target.getAmount() == null || target.getAmount().doubleValue() <= 0.0d)
            return out; // no target to bid against

        double targetCost = target.getAmount().doubleValue();
        Grain grain = ctx.operatingGrain();

        for (SnapshotRow row : ctx.rowsAt(grain)) {

            long outcomes = costOutcomes(row, ctx);
            if (outcomes < MIN_OUTCOMES_FOR_BID)
                continue;

            double spend = AnalyzerContext.spend(row);
            if (spend <= 0.0d)
                continue;

            double actualCost = spend / outcomes;
            long clicks = AnalyzerContext.clicks(row);

            if (actualCost > targetCost * OVER_RATIO) {

                double delta = Math.min(6.0d, (actualCost / targetCost - 1.0d) * 6.0d);
                ActionChange change = new ActionChange.BidChange("LOWER", target,
                        new Money(target.getAmount(), target.getCurrency()), "target_cpa");
                String rationale = String.format(
                        "Lower the bid: cost/outcome %.0f is %.0f%% over the %.0f target.",
                        actualCost, (actualCost / targetCost - 1.0d) * 100, targetCost);
                out.add(bidCandidate(row, change, rationale, delta, clicks));

            } else if (actualCost < targetCost * UNDER_RATIO
                    && row.getBlendedScore() >= HeuristicCreativeScoreProvider.WINNER_SCORE) {

                double delta = Math.min(5.0d, (1.0d - actualCost / targetCost) * 5.0d);
                ActionChange change = new ActionChange.BidChange("RAISE", target,
                        new Money(target.getAmount(), target.getCurrency()), "target_cpa");
                String rationale = String.format(
                        "Raise the bid: cost/outcome %.0f is well under the %.0f target and the grain is winning "
                                + "(score %.1f) — capture more volume.",
                        actualCost, targetCost, row.getBlendedScore());
                out.add(bidCandidate(row, change, rationale, delta, clicks));
            }
        }

        return out;
    }

    private static Candidate bidCandidate(SnapshotRow row, ActionChange change, String rationale, double delta,
            long clicks) {
        return new Candidate(
                AdzumpActionAuditActionType.ADJUST_BID,
                row.getAdGrainId(),
                change,
                rationale,
                delta,
                0.70d,
                Risk.MED,
                false,
                false,
                row.getSignalMaturity(),
                clicks,
                0L, 0L, 0L, 0L); // cost-vs-target decision; no rate-baseline proportion test
    }

    /** Outcomes for the cost basis: the target milestone, else the entry stage, else any CRM outcome. */
    static long costOutcomes(SnapshotRow row, AnalyzerContext ctx) {
        long s = 0L;
        if (ctx.targetMilestone() != null)
            s = AnalyzerContext.milestoneCount(row, ctx.targetMilestone());
        if (s == 0L && ctx.entryStage() != null)
            s = AnalyzerContext.milestoneCount(row, ctx.entryStage());
        if (s == 0L)
            s = AnalyzerContext.totalOutcomes(row);
        return s;
    }

    private static Money targetCostPerOutcome(AnalyzerContext ctx) {
        CampaignPlanBody body = ctx.plan() == null ? null : ctx.plan().getBody();
        Objective obj = body == null ? null : body.getObjective();
        return obj == null ? null : obj.getTargetCostPerOutcome();
    }
}
