package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

/**
 * Offline unit tests for {@link BidAnalyzer}: lowers the bid on a grain paying well over the target
 * cost-per-outcome, raises it on a winning grain converting under target, ignores grains with too few
 * outcomes to be stable, and stays silent when the plan has no cost target.
 */
class BidAnalyzerTest {

    private final BidAnalyzer analyzer = new BidAnalyzer();

    private PerformanceSnapshot bidSnapshot() {
        return OptimizeFixtures.snapshot(50.0, List.of(
                // qualified 5 @ 8000 -> cost 1600, over the 800 target (>1.3x) -> LOWER
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_OVER"), 10000, 200, 8000, 0.02, 5,
                        Map.of("qualified", 5L, "lead", 30L), 0.0, 45.0, SignalMaturity.MATURE),
                // qualified 20 @ 8000 -> cost 400, under 0.7x target, winning score -> RAISE
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_UNDER"), 10000, 200, 8000, 0.04,
                        20, Map.of("qualified", 20L, "lead", 40L), 0.0, 75.0, SignalMaturity.MATURE),
                // qualified 2 @ 8000 -> cost 4000 but only 2 outcomes (< min) -> ignored
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "AS_THIN"), 10000, 200, 8000, 0.02, 2,
                        Map.of("qualified", 2L, "lead", 10L), 0.0, 20.0, SignalMaturity.PARTIAL)));
    }

    @Test
    void lowersOverTarget_andRaisesUnderTargetWinner() {
        AnalyzerContext ctx = new AnalyzerContext(bidSnapshot(),
                OptimizeFixtures.plan(true, false, OptimizeFixtures.inr(800)), OptimizeFixtures.policy());

        List<Candidate> out = this.analyzer.analyze(ctx);
        assertEquals(2, out.size());

        Candidate over = out.stream().filter(c -> "AS_OVER".equals(c.target().getAdSetId())).findFirst().orElseThrow();
        assertEquals(AdzumpActionAuditActionType.ADJUST_BID, over.type());
        assertEquals("LOWER", assertInstanceOf(ActionChange.BidChange.class, over.change()).direction());

        Candidate under = out.stream().filter(c -> "AS_UNDER".equals(c.target().getAdSetId())).findFirst()
                .orElseThrow();
        assertEquals("RAISE", assertInstanceOf(ActionChange.BidChange.class, under.change()).direction());
    }

    @Test
    void silent_whenNoCostTarget() {
        AnalyzerContext ctx = new AnalyzerContext(bidSnapshot(), OptimizeFixtures.googlePlan(),
                OptimizeFixtures.policy());
        assertTrue(this.analyzer.analyze(ctx).isEmpty());
    }
}
