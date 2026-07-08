package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

/**
 * Offline unit tests for {@link AudienceAnalyzer}: excludes a junk-heavy segment, narrows a
 * below-baseline-CTR segment on demographics off a special ad category, and switches that narrow to
 * placements (never protected demographics) under HOUSING.
 */
class AudienceAnalyzerTest {

    private final AudienceAnalyzer analyzer = new AudienceAnalyzer();

    @Test
    void excludesJunkHeavySegment() {
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(30.0, List.of(
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_JUNK"), 10000, 300, 8000, 0.03, 0,
                        Map.of("lead", 40L), 0.40, 30.0, SignalMaturity.MATURE)));

        List<Candidate> out = this.analyzer.analyze(
                new AnalyzerContext(snap, OptimizeFixtures.googlePlan(), OptimizeFixtures.policy()));

        assertEquals(1, out.size());
        Candidate c = out.getFirst();
        assertEquals(AdzumpActionAuditActionType.REFINE_AUDIENCE, c.type());
        ActionChange.AudienceRefinement change = assertInstanceOf(ActionChange.AudienceRefinement.class, c.change());
        assertEquals("EXCLUDE", change.operation());
        assertEquals("audiences", change.dimension());
    }

    @Test
    void narrowsLowCtr_onDemographics_whenNotSpecialCategory() {
        assertEquals("demographics", narrowDimension(OptimizeFixtures.plan(true, false, null)));
    }

    @Test
    void narrowsLowCtr_onPlacements_underHousing() {
        assertEquals("placements", narrowDimension(OptimizeFixtures.plan(true, true, null)));
    }

    private String narrowDimension(CampaignPlan plan) {
        // A low-CTR segment beside a healthy one so the pooled baseline is above the low grain's CTR.
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(40.0, List.of(
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_LOW"), 10000, 50, 4000, 0.005, 0,
                        null, 0.0, 20.0, SignalMaturity.FAST_ONLY),
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_HIGH"), 10000, 500, 4000, 0.05, 5,
                        Map.of("lead", 20L), 0.0, 70.0, SignalMaturity.MATURE)));

        List<Candidate> out = this.analyzer.analyze(new AnalyzerContext(snap, plan, OptimizeFixtures.policy()));
        Candidate c = out.stream().filter(x -> "AS_LOW".equals(x.target().getAdSetId())).findFirst().orElseThrow();
        assertEquals(AdzumpActionAuditActionType.REFINE_AUDIENCE, c.type());
        return assertInstanceOf(ActionChange.AudienceRefinement.class, c.change()).dimension();
    }
}
