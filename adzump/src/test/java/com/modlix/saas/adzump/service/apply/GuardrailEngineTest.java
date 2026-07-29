package com.modlix.saas.adzump.service.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.Risk;
import com.modlix.saas.adzump.service.optimize.SignificanceGate;

/**
 * J13 §5.2 guardrails, enforced at apply time against live {@link CampaignState}: a breach downgrades
 * (QUEUE / SUPPRESS) or rejects (stale) — never a silent apply. Uses the real {@link SignificanceGate}
 * (reused for the do-no-harm / maturity re-assertion), no mocks.
 */
class GuardrailEngineTest {

    private final GuardrailEngine engine = new GuardrailEngine(new SignificanceGate());

    private static final AutonomyPolicy HYBRID = AutonomyPolicy.from(ApplyFixtures.hybrid(20.0d));

    private GuardrailVerdict check(Action action, AutonomyPolicy policy, CampaignState state) {
        return this.engine.check(action, policy, state);
    }

    // ---- stale-action rejection --------------------------------------------------------------

    @Test
    void staleAction_rejected() {
        AutonomyPolicy policy = AutonomyPolicy.from(ApplyFixtures.autonomy("AUTONOMOUS", 20.0d, null, 6L, 0L));
        PerformanceSnapshot snapshot = ApplyFixtures.snapshot(LocalDateTime.now().minusHours(10), List.of());
        CampaignState state = new CampaignState(ApplyFixtures.plan(100), snapshot, LocalDateTime.now(), Map.of());

        Action action = ApplyFixtures.negativeKeyword(ApplyFixtures.adSetGrain("C1", "AS1"), Risk.LOW);

        GuardrailVerdict verdict = check(action, policy, state);
        assertEquals(GuardrailVerdict.Outcome.REJECT, verdict.outcome());
        assertEquals("stale_snapshot", verdict.reason());
    }

    // ---- rate / frequency --------------------------------------------------------------------

    @Test
    void tooFrequentChangeSameEntity_queued() {
        AutonomyPolicy policy = AutonomyPolicy.from(ApplyFixtures.autonomy("AUTONOMOUS", 20.0d, null, 0L, 6L));
        AdGrainId target = ApplyFixtures.adSetGrain("C1", "AS1");
        Map<String, LocalDateTime> lastApplied = Map.of(CampaignState.grainKey(target), LocalDateTime.now().minusHours(1));

        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.snapshot(LocalDateTime.now(), List.of()), LocalDateTime.now(), lastApplied);

        GuardrailVerdict verdict = check(ApplyFixtures.negativeKeyword(target, Risk.LOW), policy, state);
        assertEquals(GuardrailVerdict.Outcome.QUEUE, verdict.outcome());
        assertEquals("rate_limited", verdict.reason());
    }

    // ---- significance / maturity + do-no-harm (pause) ----------------------------------------

    @Test
    void killOnFastOnlySignal_suppressed() {
        AdGrainId target = ApplyFixtures.adGrain("C1", "AD_SLOW");
        List<SnapshotRow> rows = new ArrayList<>();
        // target: a converter but only FAST_ONLY signal, enough volume — a kill must wait for MATURE.
        rows.add(ApplyFixtures.row(Grain.AD, target, 4000, 150, Map.of("lead", 5L), SignalMaturity.FAST_ONLY));
        // a second converter, so the target is NOT the only converter (isolates the maturity check).
        rows.add(ApplyFixtures.row(Grain.AD, ApplyFixtures.adGrain("C1", "AD_OK"), 4000, 150,
                Map.of("lead", 20L, "qualified", 10L), SignalMaturity.MATURE));

        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.snapshot(LocalDateTime.now(), rows), LocalDateTime.now(), Map.of());

        GuardrailVerdict verdict = check(ApplyFixtures.pause(target, true, Risk.HIGH), HYBRID, state);
        assertEquals(GuardrailVerdict.Outcome.SUPPRESS, verdict.outcome());
        assertEquals("kill_needs_mature_signal", verdict.reason());
    }

    @Test
    void pauseTheOnlyConverter_suppressedByDoNoHarm() {
        AdGrainId target = ApplyFixtures.adGrain("C1", "AD_ONLY");
        List<SnapshotRow> rows = new ArrayList<>();
        // the only converter, MATURE + high volume: do-no-harm must still refuse to zero it.
        rows.add(ApplyFixtures.row(Grain.AD, target, 8000, 300, Map.of("lead", 20L, "qualified", 8L),
                SignalMaturity.MATURE));

        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.snapshot(LocalDateTime.now(), rows), LocalDateTime.now(), Map.of());

        GuardrailVerdict verdict = check(ApplyFixtures.pause(target, true, Risk.HIGH), HYBRID, state);
        assertEquals(GuardrailVerdict.Outcome.SUPPRESS, verdict.outcome());
        assertEquals("do_no_harm_only_converter", verdict.reason());
    }

    @Test
    void pauseWouldLeaveZeroActiveAds_queued() {
        AdGrainId target = ApplyFixtures.adGrain("C1", "AD_LAST");
        // a single active non-converting AD: pausing it would leave the campaign with zero active ads.
        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.loneNonConverterSnapshot(target), LocalDateTime.now(), Map.of());

        GuardrailVerdict verdict = check(ApplyFixtures.pause(target, false, Risk.MED), HYBRID, state);
        assertEquals(GuardrailVerdict.Outcome.QUEUE, verdict.outcome());
        assertEquals("would_leave_zero_active_ads", verdict.reason());
    }

    // ---- budget caps + max-change-per-run (re-checked against live budget) --------------------

    @Test
    void budgetShiftExceedingMaxChangePerRun_queued() {
        // live daily budget 100; a +50 shift is a 50% swing, over the 20% cap.
        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.snapshot(LocalDateTime.now(), List.of()), LocalDateTime.now(), Map.of());

        Action shift = ApplyFixtures.budgetShift(ApplyFixtures.adGrain("C1", "A"),
                ApplyFixtures.adSetGrain("C1", "AS1"), 50, 0.10, Risk.MED);

        GuardrailVerdict verdict = check(shift, HYBRID, state);
        assertEquals(GuardrailVerdict.Outcome.QUEUE, verdict.outcome());
        assertEquals("exceeds_max_change_per_run", verdict.reason());
    }

    @Test
    void budgetShiftExceedingDailyCap_queued() {
        AutonomyPolicy policy = AutonomyPolicy.from(ApplyFixtures.autonomy("AUTONOMOUS", 20.0d, 105.0d, 0L, 0L));
        // live daily budget 100; a +10 shift (10% swing, within cap) lands at 110 > the 105 daily cap.
        CampaignState state = new CampaignState(ApplyFixtures.plan(100),
                ApplyFixtures.snapshot(LocalDateTime.now(), List.of()), LocalDateTime.now(), Map.of());

        Action shift = ApplyFixtures.budgetShift(ApplyFixtures.adGrain("C1", "A"),
                ApplyFixtures.adSetGrain("C1", "AS1"), 10, 0.10, Risk.MED);

        GuardrailVerdict verdict = check(shift, policy, state);
        assertEquals(GuardrailVerdict.Outcome.QUEUE, verdict.outcome());
        assertEquals("exceeds_daily_budget_cap", verdict.reason());
    }

    // ---- the clear path ----------------------------------------------------------------------

    @Test
    void cleanAction_passes() {
        CampaignPlan plan = ApplyFixtures.plan(100);
        CampaignState state = new CampaignState(plan, ApplyFixtures.snapshot(LocalDateTime.now(), List.of()),
                LocalDateTime.now(), Map.of());

        GuardrailVerdict verdict = check(ApplyFixtures.negativeKeyword(ApplyFixtures.adSetGrain("C1", "AS1"),
                Risk.LOW), HYBRID, state);
        assertEquals(GuardrailVerdict.Outcome.OK, verdict.outcome());
    }
}
