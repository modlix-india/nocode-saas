package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Offline unit tests for {@link CreativeAnalyzer} (consulting the heuristic {@link CreativeScoreProvider}
 * J20 seam): pauses a zero-outcome ad as a low-risk trim, proposes a kill of a low-score converter
 * (marking it a kill so the gate can enforce maturity), rotates a mediocre converter, and requests
 * variants from a mature winner.
 */
class CreativeAnalyzerTest {

    private final CreativeAnalyzer analyzer = new CreativeAnalyzer(new HeuristicCreativeScoreProvider());

    private AnalyzerContext ctx(PerformanceSnapshot snap) {
        return new AnalyzerContext(snap, OptimizeFixtures.googlePlan(), OptimizeFixtures.policy());
    }

    private Candidate only(List<Candidate> out, AdzumpActionAuditActionType type, String adId) {
        return out.stream().filter(c -> c.type() == type && adId.equals(c.target().getAdId())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " for " + adId + " in " + out));
    }

    @Test
    void pausesTheZeroOutcomeAd_asALowRiskTrim() {
        List<Candidate> out = this.analyzer.analyze(ctx(OptimizeFixtures.underperformer()));

        Candidate c = only(out, AdzumpActionAuditActionType.PAUSE_ENTITY, "AD_DEAD");
        assertFalse(c.kill()); // waste trim, not a kill of a converter
        // rollup(40) * spendShare(4000/16000 = 0.25) = 10.0
        assertEquals(10.0d, c.expectedDelta(), 1e-9);
    }

    @Test
    void proposesKillOfLowScoreConverter_markedAsKill() {
        List<Candidate> out = this.analyzer.analyze(ctx(OptimizeFixtures.slowConverter()));

        Candidate c = only(out, AdzumpActionAuditActionType.PAUSE_ENTITY, "AD_SLOW");
        assertTrue(c.kill());
        assertFalse(c.onlyConverter()); // AD_OK also converts
        assertEquals(SignalMaturity.PARTIAL, c.maturity());
        // No pause proposed for the healthy AD_OK.
        assertTrue(out.stream().noneMatch(x -> "AD_OK".equals(x.target().getAdId())));
    }

    @Test
    void rotatesMediocre_andRequestsVariantsFromWinner() {
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(60.0, List.of(
                OptimizeFixtures.row(Grain.CAMPAIGN, OptimizeFixtures.campaignGrain("C"), 20000, 400, 16000, 0.03, 0,
                        Map.of("lead", 40L, "qualified", 20L), 0.0, 60.0, SignalMaturity.MATURE),
                OptimizeFixtures.row(Grain.AD, OptimizeFixtures.adGrain("C", "AD_WIN"), 5000, 200, 8000, 0.04, 15,
                        Map.of("lead", 30L, "qualified", 15L), 0.0, 85.0, SignalMaturity.MATURE),
                OptimizeFixtures.row(Grain.AD, OptimizeFixtures.adGrain("C", "AD_MID"), 5000, 150, 6000, 0.03, 8,
                        Map.of("lead", 10L), 0.0, 40.0, SignalMaturity.MATURE)));

        List<Candidate> out = this.analyzer.analyze(ctx(snap));

        Candidate variant = only(out, AdzumpActionAuditActionType.REQUEST_VARIANT, "AD_WIN");
        assertEquals(Risk.LOW, variant.risk());

        Candidate rotate = only(out, AdzumpActionAuditActionType.ROTATE_CREATIVE, "AD_MID");
        assertFalse(rotate.kill());
    }
}
