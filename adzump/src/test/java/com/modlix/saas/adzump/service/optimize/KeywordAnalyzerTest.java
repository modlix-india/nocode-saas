package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

/**
 * Offline unit tests for {@link KeywordAnalyzer}: on a Google-search plan it negative-keywords a
 * zero-outcome ad group that has burned real spend/clicks (a LOW-risk, no-baseline containment), stays
 * silent on non-Google plans, and does not fire on a handful of clicks below the legacy critical
 * thresholds.
 */
class KeywordAnalyzerTest {

    private final KeywordAnalyzer analyzer = new KeywordAnalyzer();

    @Test
    void negativeKeywordsTheWastefulGoogleAdGroup() {
        AnalyzerContext ctx = new AnalyzerContext(OptimizeFixtures.underperformer(),
                OptimizeFixtures.googlePlan(), OptimizeFixtures.policy());

        List<Candidate> out = this.analyzer.analyze(ctx);

        assertEquals(1, out.size());
        Candidate c = out.getFirst();
        assertEquals(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD, c.type());
        assertEquals("AS_LOSE", c.target().getAdSetId());
        assertEquals(Risk.LOW, c.risk());
        assertEquals(0L, c.baselineTrials()); // absolute waste, no rate baseline
        ActionChange.NegativeKeyword change = assertInstanceOf(ActionChange.NegativeKeyword.class, c.change());
        assertEquals(MatchType.BROAD, change.matchType());
    }

    @Test
    void silent_onNonGooglePlan() {
        AnalyzerContext ctx = new AnalyzerContext(OptimizeFixtures.underperformer(),
                OptimizeFixtures.plan(false, false, null), OptimizeFixtures.policy());
        assertTrue(this.analyzer.analyze(ctx).isEmpty());
    }

    @Test
    void silent_whenWasteBelowCriticalThresholds() {
        // A zero-outcome ad group, but only 20 clicks / 500 spend -> below the critical thresholds.
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(30.0, List.of(
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "TINY"), 1000, 20, 500, 0.02, 0,
                        null, 0.0, 0.0, SignalMaturity.FAST_ONLY)));
        AnalyzerContext ctx = new AnalyzerContext(snap, OptimizeFixtures.googlePlan(), OptimizeFixtures.policy());
        assertTrue(this.analyzer.analyze(ctx).isEmpty());
    }
}
