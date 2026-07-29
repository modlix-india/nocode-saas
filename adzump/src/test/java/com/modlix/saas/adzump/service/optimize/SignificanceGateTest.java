package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.service.optimize.SignificanceGate.GateConfig;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict.GateOutcome;

/**
 * Offline unit tests for the load-bearing {@link SignificanceGate}: each of the four checks
 * (min-volume, do-no-harm, signal-maturity, statistical significance) is exercised in isolation, the
 * precedence between them is pinned, and the config parse from policy/autonomy is verified.
 */
class SignificanceGateTest {

    private final SignificanceGate gate = new SignificanceGate();
    private final GateConfig cfg = new GateConfig(30L, 0.90d, true, true, Integer.MAX_VALUE);

    private static Candidate cand(long volume, boolean kill, boolean onlyConverter, SignalMaturity maturity,
            long obsTrials, long obsSucc, long baseTrials, long baseSucc) {
        return new Candidate(AdzumpActionAuditActionType.PAUSE_ENTITY, new AdGrainId().setAdId("ad"),
                new ActionChange.Pause(kill, "x"), "rationale", 1.0d, 0.5d, Risk.MED,
                kill, onlyConverter, maturity, volume, obsTrials, obsSucc, baseTrials, baseSucc);
    }

    // ---- min volume --------------------------------------------------------------------------

    @Test
    void suppresses_belowMinVolume() {
        SignificanceVerdict v = this.gate.evaluate(
                cand(10L, false, false, SignalMaturity.MATURE, 0, 0, 0, 0), this.cfg);
        assertEquals(GateOutcome.MIN_VOLUME, v.outcome());
        assertFalse(v.passed());
    }

    @Test
    void passes_whenVolumeMetAndNoBaselineComparison() {
        // Absolute-waste style action: enough volume, no rate baseline -> passes on volume alone.
        SignificanceVerdict v = this.gate.evaluate(
                cand(120L, false, false, SignalMaturity.FAST_ONLY, 0, 0, 0, 0), this.cfg);
        assertTrue(v.passed());
    }

    // ---- statistical significance ------------------------------------------------------------

    @Test
    void suppresses_whenDifferenceVsBaselineIsNotSignificant() {
        // 10/100 vs 11/100 -> |z| ~ 0.23, well below the 0.90 critical value.
        SignificanceVerdict v = this.gate.evaluate(
                cand(100L, false, false, SignalMaturity.MATURE, 100, 10, 100, 11), this.cfg);
        assertEquals(GateOutcome.NOT_SIGNIFICANT, v.outcome());
    }

    @Test
    void passes_whenDifferenceVsBaselineIsSignificant() {
        // 0/200 (loser) vs 25/200 (winner) -> |z| ~ 5, clears the gate.
        SignificanceVerdict v = this.gate.evaluate(
                cand(200L, false, false, SignalMaturity.FAST_ONLY, 200, 0, 200, 25), this.cfg);
        assertTrue(v.passed());
    }

    // ---- signal maturity (fast proposes, slow disposes) --------------------------------------

    @Test
    void suppresses_killOfConverter_onImmatureSignal() {
        SignificanceVerdict v = this.gate.evaluate(
                cand(150L, true, false, SignalMaturity.PARTIAL, 0, 0, 0, 0), this.cfg);
        assertEquals(GateOutcome.IMMATURE_SIGNAL, v.outcome());
    }

    @Test
    void passes_killOfConverter_onMatureSignal() {
        SignificanceVerdict v = this.gate.evaluate(
                cand(150L, true, false, SignalMaturity.MATURE, 0, 0, 0, 0), this.cfg);
        assertTrue(v.passed());
    }

    // ---- do-no-harm --------------------------------------------------------------------------

    @Test
    void suppresses_killOfOnlyConverter_doNoHarm_evenWhenMature() {
        SignificanceVerdict v = this.gate.evaluate(
                cand(150L, true, true, SignalMaturity.MATURE, 0, 0, 0, 0), this.cfg);
        assertEquals(GateOutcome.DO_NO_HARM, v.outcome());
    }

    @Test
    void doNoHarm_takesPrecedenceOverMaturity() {
        // Only converter AND immature: do-no-harm is the reported (stronger) reason.
        SignificanceVerdict v = this.gate.evaluate(
                cand(150L, true, true, SignalMaturity.PARTIAL, 0, 0, 0, 0), this.cfg);
        assertEquals(GateOutcome.DO_NO_HARM, v.outcome());
    }

    // ---- config parse ------------------------------------------------------------------------

    @Test
    void config_readsGatesFromPolicy_andCapsFromAutonomy() {
        GateConfig c = this.gate.config(OptimizeFixtures.policy(), OptimizeFixtures.autonomyWithMaxChanges(3));
        assertEquals(30L, c.minVolume());
        assertEquals(0.90d, c.confidence(), 1e-9);
        assertEquals(3, c.maxChangesPerRun());
        assertTrue(c.doNoHarm());
        assertTrue(c.fastPauseSlowKill());
    }

    @Test
    void config_defaults_whenNothingConfigured() {
        GateConfig c = this.gate.config(null, null);
        assertEquals(SignificanceGate.DEFAULT_MIN_VOLUME, c.minVolume());
        assertEquals(SignificanceGate.DEFAULT_CONFIDENCE, c.confidence(), 1e-9);
        assertEquals(SignificanceGate.DEFAULT_MAX_CHANGES, c.maxChangesPerRun());
        assertTrue(c.doNoHarm());
    }
}
