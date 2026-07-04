package com.modlix.saas.adzump.service.optimize;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Audience dimension (J12 §5.2): narrow / exclude underperforming segments — the targeting-quality
 * signal, distinct from the score-gap signal budget acts on. Two triggers ported from the legacy
 * demographic / location optimizers:
 * <ul>
 * <li><b>junk exclusion</b> — a segment whose leadzump leads keep getting tagged junk (junk rate over
 * {@link #JUNK_THRESHOLD}) is proposed for exclusion (interest/audience level — allowed even under a
 * special ad category);</li>
 * <li><b>relevance narrowing</b> — a segment with materially below-baseline CTR is proposed for
 * narrowing.</li>
 * </ul>
 *
 * <p><b>Special ad category (HOUSING/EMPLOYMENT/CREDIT/FINANCIAL):</b> demographic dimensions
 * (age/gender/ZIP) are locked, so a narrow is proposed on placements only — never on protected
 * demographics.
 */
@Service
public class AudienceAnalyzer implements DimensionAnalyzer {

    /** Exclude a segment once this fraction of its leads are junk. */
    static final double JUNK_THRESHOLD = 0.30d;

    /** Narrow a segment whose CTR falls below this (and materially below the campaign baseline). */
    static final double LOW_CTR = 0.01d;

    @Override
    public List<Candidate> analyze(AnalyzerContext ctx) {

        List<Candidate> out = new ArrayList<>();
        Grain grain = ctx.operatingGrain();
        List<SnapshotRow> rows = ctx.rowsAt(grain);

        long pooledClicks = 0L;
        long pooledImpressions = 0L;
        for (SnapshotRow r : rows) {
            pooledClicks += AnalyzerContext.clicks(r);
            pooledImpressions += AnalyzerContext.impressions(r);
        }

        for (SnapshotRow row : rows) {

            double junk = AnalyzerContext.junkRate(row);
            long leads = AnalyzerContext.totalOutcomes(row);

            if (junk >= JUNK_THRESHOLD && leads > 0) {
                double delta = Math.min(5.0d, junk * 8.0d);
                ActionChange change = new ActionChange.AudienceRefinement("EXCLUDE", "audiences",
                        String.format("junk rate %.0f%% on this segment", junk * 100));
                String rationale = String.format(
                        "Exclude the segment: %.0f%% of its leads are tagged junk in leadzump.", junk * 100);
                // Junk rate is a direct quality signal; the volume gate on leads guards it (no rate baseline).
                out.add(new Candidate(AdzumpActionAuditActionType.REFINE_AUDIENCE, row.getAdGrainId(), change,
                        rationale, delta, 0.65d, Risk.LOW, false, false, row.getSignalMaturity(),
                        leads, 0L, 0L, 0L, 0L));
                continue;
            }

            double ctr = AnalyzerContext.ctr(row);
            long impressions = AnalyzerContext.impressions(row);
            double pooledCtr = pooledImpressions > 0 ? (double) pooledClicks / pooledImpressions : 0.0d;

            if (ctr < LOW_CTR && ctr < pooledCtr && impressions > 0) {
                String dimension = ctx.isHousing() ? "placements" : "demographics";
                double delta = Math.min(4.0d, (LOW_CTR - ctr) / LOW_CTR * 4.0d);
                ActionChange change = new ActionChange.AudienceRefinement("NARROW", dimension,
                        String.format("CTR %.2f%% vs %.2f%% campaign baseline", ctr * 100, pooledCtr * 100));
                String rationale = String.format(
                        "Narrow the segment (%s): CTR %.2f%% is below the %.2f%% campaign baseline%s.",
                        dimension, ctr * 100, pooledCtr * 100,
                        ctx.isHousing() ? " (demographics locked under the special ad category)" : "");
                out.add(new Candidate(AdzumpActionAuditActionType.REFINE_AUDIENCE, row.getAdGrainId(), change,
                        rationale, delta, 0.60d, Risk.LOW, false, false, row.getSignalMaturity(),
                        impressions, impressions, AnalyzerContext.clicks(row), pooledImpressions, pooledClicks));
            }
        }

        return out;
    }
}
