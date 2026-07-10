package com.modlix.saas.adzump.service.creative;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.feedback.PolicyScorer;

/**
 * J20 §5.1 — creative-grain scoring. Reuses the J10 {@link PolicyScorer} <b>at the creative grain</b>:
 * the score of a creative is the spend-weighted rollup of the snapshot AD-grain rows that carry it,
 * reusing the per-row blended scores J10 already computed under the effective {@code PerformancePolicy}
 * (so the creative score is consistent with the campaign score, not a second, divergent scoring).
 *
 * <p><b>Creative &harr; AD grain (P3 join).</b> A {@link PerformanceSnapshot} is keyed by the platform
 * ad-grain id, not the plan-body creative id. In P3 the AD grain <i>is</i> the creative grain (one
 * launched ad wraps one creative), so this joins {@code creativeId} to the AD row's
 * {@link com.modlix.saas.adzump.model.leadzump.AdGrainId#getAdId() adId}. TODO(J13/launch): replace the
 * exact-id match with the persisted creative&rarr;platform-ad id map recorded at launch, so the join
 * survives when the platform mints an ad id distinct from the creative id.
 *
 * <p><b>Maturity is carried, not collapsed.</b> A creative that spans several ad rows takes the
 * most-mature row's {@link SignalMaturity}; {@link CreativeScore#judgeable()} gates whether the loop may
 * treat the creative as a proven win/loss. Deterministic Java, never an LLM.
 */
@Service
public class CreativeScorer {

    private final PolicyScorer policyScorer;

    public CreativeScorer(PolicyScorer policyScorer) {
        this.policyScorer = policyScorer;
    }

    /** {@link #score(String, List)} over the snapshot's grain rows. */
    public CreativeScore score(String creativeId, PerformanceSnapshot snapshot) {
        return this.score(creativeId, snapshot == null ? null : snapshot.getGrainRows());
    }

    /**
     * The creative-grain score over a set of snapshot grain rows: selects the AD-grain rows whose
     * {@code adId} matches {@code creativeId}, rolls their blended scores up (spend-weighted via
     * {@link PolicyScorer#rollup}), and carries the aggregated volume, junk rate, and maturity. Returns
     * {@link CreativeScore#empty} when no AD row joins (no signal for this creative yet).
     */
    public CreativeScore score(String creativeId, List<SnapshotRow> rows) {

        if (creativeId == null || creativeId.isBlank() || rows == null || rows.isEmpty())
            return CreativeScore.empty(creativeId);

        List<SnapshotRow> matched = new ArrayList<>();
        for (SnapshotRow row : rows) {
            if (row != null && row.getGrain() == Grain.AD && row.getAdGrainId() != null
                    && creativeId.equals(row.getAdGrainId().getAdId()))
                matched.add(row);
        }

        if (matched.isEmpty())
            return CreativeScore.empty(creativeId);

        double score = this.policyScorer.rollup(matched);

        long volume = 0L;
        double junkWeighted = 0.0d;
        SignalMaturity maturity = SignalMaturity.FAST_ONLY;

        for (SnapshotRow row : matched) {
            long count = totalCount(row.getCrm());
            volume += count;
            if (row.getCrm() != null)
                junkWeighted += row.getCrm().getJunkRate() * count;
            maturity = maxMaturity(maturity, row.getSignalMaturity());
        }

        double junkRate = volume > 0 ? junkWeighted / volume : 0.0d;
        boolean judgeable = maturity == SignalMaturity.MATURE;

        return new CreativeScore(creativeId, score, volume, junkRate, maturity, judgeable, matched.size());
    }

    private static long totalCount(CrmMetrics crm) {
        if (crm == null || crm.getCountByMilestone() == null)
            return 0L;
        long total = 0L;
        for (Long v : crm.getCountByMilestone().values())
            total += v == null ? 0L : v;
        return total;
    }

    private static SignalMaturity maxMaturity(SignalMaturity a, SignalMaturity b) {
        if (b == null)
            return a;
        return b.ordinal() > a.ordinal() ? b : a;
    }
}
