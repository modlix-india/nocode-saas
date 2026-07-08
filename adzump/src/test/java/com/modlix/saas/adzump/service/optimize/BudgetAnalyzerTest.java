package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
 * Offline unit tests for {@link BudgetAnalyzer}: shifts off the low-score grain onto the high-score
 * grain (carrying the winner as the significance baseline), stays quiet when the gap is immaterial,
 * and never shifts off the only converter (analyzer-level do-no-harm).
 */
class BudgetAnalyzerTest {

    private final BudgetAnalyzer analyzer = new BudgetAnalyzer();

    private AnalyzerContext ctx(PerformanceSnapshot snap) {
        return new AnalyzerContext(snap, OptimizeFixtures.googlePlan(), OptimizeFixtures.policy());
    }

    @Test
    void shiftsOffTheLoser_ontoTheWinner_withWinnerAsBaseline() {
        List<Candidate> out = this.analyzer.analyze(ctx(OptimizeFixtures.underperformer()));

        assertEquals(1, out.size());
        Candidate c = out.getFirst();
        assertEquals(AdzumpActionAuditActionType.SHIFT_BUDGET, c.type());
        assertEquals("AS_LOSE", c.target().getAdSetId());
        assertFalse(c.kill());

        ActionChange.BudgetShift shift = assertInstanceOf(ActionChange.BudgetShift.class, c.change());
        assertEquals("AS_WIN", shift.toGrain().getAdSetId());
        assertEquals(1600.0d, shift.amount().getAmount().doubleValue(), 1e-9); // 20% of the 8000 loser spend

        // (winnerScore - loserScore) * (shift / campaignSpend) = 80 * (1600/16000) = 8.0
        assertEquals(8.0d, c.expectedDelta(), 1e-9);

        // Significance baseline is the winner grain; observed is the loser.
        assertEquals(0L, c.observedSuccesses());
        assertEquals(200L, c.baselineTrials());
        assertEquals(25L, c.baselineSuccesses());
    }

    @Test
    void noShift_whenScoreGapImmaterial() {
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(52.0, List.of(
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "A"), 10000, 200, 8000, 0.02, 10,
                        Map.of("lead", 20L), 0.0, 50.0, SignalMaturity.MATURE),
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "B"), 10000, 200, 8000, 0.02, 11,
                        Map.of("lead", 22L), 0.0, 55.0, SignalMaturity.MATURE)));

        assertTrue(this.analyzer.analyze(ctx(snap)).isEmpty());
    }

    @Test
    void noShift_whenLoserIsTheOnlyConverter() {
        // The lower-score grain is the ONLY one still producing outcomes -> never reallocate away from it.
        PerformanceSnapshot snap = OptimizeFixtures.snapshot(45.0, List.of(
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "CONV"), 10000, 200, 8000, 0.02, 5,
                        Map.of("lead", 20L), 0.0, 30.0, SignalMaturity.MATURE),
                OptimizeFixtures.row(Grain.ADSET, OptimizeFixtures.adSetGrain("C", "DRY"), 10000, 200, 8000, 0.02, 0,
                        null, 0.0, 60.0, SignalMaturity.FAST_ONLY)));

        assertTrue(this.analyzer.analyze(ctx(snap)).isEmpty());
    }
}
