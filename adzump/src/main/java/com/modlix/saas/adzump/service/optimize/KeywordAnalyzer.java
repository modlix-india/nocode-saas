package com.modlix.saas.adzump.service.optimize;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Keyword / search-term dimension (J12 §5.2), Google search family only. Ported from the legacy
 * search-term / keyword optimizers, which flagged waste with these deterministic thresholds:
 * <b>critical</b> spend (≥ {@link #CRITICAL_COST}) or clicks (≥ {@link #CRITICAL_CLICKS}) with zero
 * conversions. When a Google ad group is spending on traffic that produces <b>no</b> outcome, the
 * cheapest, safest containment is to add negative keywords (never removes converting reach), so this
 * is a {@link Risk#LOW} action justified by absolute volume (no rate baseline).
 *
 * <p><b>P3 limitation:</b> the J10 snapshot has no search-term grain, so the exact wasteful terms are
 * not named here — the {@link ActionChange.NegativeKeyword#terms()} list is empty and the terms are
 * resolved from the search-term report at apply time. <b>TODO(J10):</b> add a search-term grain so the
 * analyzer can name terms and also pause weak positive keywords.
 */
@Service
public class KeywordAnalyzer implements DimensionAnalyzer {

    /** Legacy critical thresholds: zero-conversion spend/clicks above these are wasteful, not noise. */
    static final double CRITICAL_COST = 2000.0d;
    static final long CRITICAL_CLICKS = 50L;

    @Override
    public List<Candidate> analyze(AnalyzerContext ctx) {

        List<Candidate> out = new ArrayList<>();
        if (!ctx.isGoogleSearch())
            return out; // negative keywords only apply to Google search-family campaigns

        Grain grain = ctx.operatingGrain();
        double campaignSpend = ctx.campaignSpend();

        for (SnapshotRow row : ctx.rowsAt(grain)) {

            if (!AnalyzerContext.isPureWaste(row))
                continue;

            long clicks = AnalyzerContext.clicks(row);
            double spend = AnalyzerContext.spend(row);
            if (clicks < CRITICAL_CLICKS && spend < CRITICAL_COST)
                continue; // real waste, not a handful of clicks

            double wasteShare = campaignSpend > 0.0d ? spend / campaignSpend : 0.0d;
            double delta = Math.min(3.0d, wasteShare * 6.0d);

            ActionChange change = new ActionChange.NegativeKeyword(MatchType.BROAD, List.of(),
                    "zero-outcome spend on broad-match traffic");
            String rationale = String.format(
                    "Add negative keywords: this ad group spent %.0f over %d clicks with no conversions — "
                            + "contain the wasteful search traffic before pausing.",
                    spend, clicks);

            // Absolute waste: justified by volume, so no rate baseline (baselineTrials = 0).
            out.add(new Candidate(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD, row.getAdGrainId(), change,
                    rationale, delta, 0.70d, Risk.LOW, false, false, row.getSignalMaturity(),
                    clicks, 0L, 0L, 0L, 0L));
        }

        return out;
    }
}
