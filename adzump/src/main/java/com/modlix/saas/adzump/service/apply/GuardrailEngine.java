package com.modlix.saas.adzump.service.apply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionChange;
import com.modlix.saas.adzump.service.optimize.SignificanceGate;
import com.modlix.saas.adzump.service.optimize.SignificanceGate.GateConfig;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict;

/**
 * J13 §5.2 — the load-bearing guardrails, enforced <b>at apply time</b> (not just when the action was
 * proposed; live state may have changed). Every action the {@link AutonomyRouter} marked
 * {@link ApplyRoute#APPLY} passes through {@link #check} against live {@link CampaignState} before the
 * {@link ActionApplier} touches the mutation spine. A breach is <b>never a silent apply</b>: it is
 * downgraded to {@link GuardrailVerdict.Outcome#QUEUE} (surfaced for a human), {@link GuardrailVerdict.Outcome#SUPPRESS}
 * (logged), or {@link GuardrailVerdict.Outcome#REJECT} (stale) — with a logged reason.
 *
 * <p>The checks, in order:
 * <ol>
 * <li><b>Stale-action rejection</b> — an action built off a snapshot older than
 * {@code staleSnapshotMaxAgeHours} is REJECTED (the facts it reasoned on are gone).</li>
 * <li><b>Rate / frequency</b> — the same entity may not be changed more often than
 * {@code minHoursBetweenChangesPerEntity}; a too-soon repeat is QUEUED (no thrashing).</li>
 * <li><b>Significance / maturity + do-no-harm</b> — a pause that is a <b>kill of a converter</b> is
 * re-judged through the shared {@link SignificanceGate}: it needs {@code MATURE} CRM signal (fast signal
 * may only trim), never zeroes the only converter, and re-asserts min-volume. Plus: a pause must never
 * leave the campaign with zero active ads.</li>
 * <li><b>Budget caps + max-change-per-run</b> — a budget shift is re-checked against the campaign's
 * <b>live</b> daily budget: no run may swing it more than {@code maxChangePerRunPct}, exceed the absolute
 * {@code dailyBudgetCap}, exceed {@code budgetCapFactor}× the current budget, or drop it to/below zero.</li>
 * </ol>
 * Pure and deterministic given a {@link CampaignState}; reuses {@link SignificanceGate} so "is this real /
 * safe?" has one definition across propose (J12) and apply (J13).
 */
@Service
public class GuardrailEngine {

    private static final Logger logger = LoggerFactory.getLogger(GuardrailEngine.class);

    // Apply-time re-assertion of volume/significance uses the same conservative defaults as J12's gate.
    private static final long RECHECK_MIN_VOLUME = 30L;
    private static final double RECHECK_CONFIDENCE = 0.90d;

    private final SignificanceGate significanceGate;

    public GuardrailEngine(SignificanceGate significanceGate) {
        this.significanceGate = significanceGate;
    }

    /**
     * Runs every guardrail for {@code action} against {@code state} under {@code policy}. Returns
     * {@link GuardrailVerdict#ok()} only when nothing was breached.
     */
    public GuardrailVerdict check(Action action, AutonomyPolicy policy, CampaignState state) {

        AdGrainId target = action.target();

        // 1. Stale-action rejection.
        if (policy.staleSnapshotMaxAgeHours() > 0L) {
            java.time.LocalDateTime takenAt = state.snapshotTakenAt();
            java.time.LocalDateTime cutoff = state.now().minusHours(policy.staleSnapshotMaxAgeHours());
            if (takenAt == null || takenAt.isBefore(cutoff))
                return logged(action, GuardrailVerdict.reject("stale_snapshot"));
        }

        // 2. Rate / frequency — do not thrash the same entity.
        if (policy.minHoursBetweenChangesPerEntity() > 0L) {
            java.time.LocalDateTime last = state.lastAppliedAt(target);
            java.time.LocalDateTime cutoff = state.now().minusHours(policy.minHoursBetweenChangesPerEntity());
            if (last != null && last.isAfter(cutoff))
                return logged(action, GuardrailVerdict.queue("rate_limited"));
        }

        // 3. Pause-specific: significance / maturity / do-no-harm + never-zero-active-ads.
        if (action.type() == com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.PAUSE_ENTITY) {
            GuardrailVerdict pause = checkPause(policy, state, target);
            if (pause != null)
                return logged(action, pause);
        }

        // 4. Budget caps + max-change-per-run (re-checked against live budget).
        if (action.type() == com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.SHIFT_BUDGET
                && action.change() instanceof ActionChange.BudgetShift shift) {
            GuardrailVerdict budget = checkBudget(shift, policy, state);
            if (budget != null)
                return logged(action, budget);
        }

        return GuardrailVerdict.cleared();
    }

    // ---- pause guardrails --------------------------------------------------------------------

    private GuardrailVerdict checkPause(AutonomyPolicy policy, CampaignState state, AdGrainId target) {

        boolean converter = state.isConverter(target);

        // A pause of a converter is a KILL: re-assert do-no-harm + maturity through the shared gate.
        if (converter) {
            boolean onlyConverter = policy.doNoHarm() && state.isOnlyConverter(target);
            GateConfig cfg = new GateConfig(RECHECK_MIN_VOLUME, RECHECK_CONFIDENCE, policy.doNoHarm(),
                    policy.fastPauseSlowKill(), Integer.MAX_VALUE);

            SignificanceVerdict verdict = this.significanceGate.evaluatePauseGuardrail(true, onlyConverter,
                    state.maturityFor(target), state.volumeFor(target), cfg);

            if (!verdict.passed())
                return switch (verdict.outcome()) {
                    case DO_NO_HARM -> GuardrailVerdict.suppress("do_no_harm_only_converter");
                    case IMMATURE_SIGNAL -> GuardrailVerdict.suppress("kill_needs_mature_signal");
                    case MIN_VOLUME -> GuardrailVerdict.queue("insufficient_volume");
                    case NOT_SIGNIFICANT -> GuardrailVerdict.queue("not_significant");
                    default -> GuardrailVerdict.queue("gate_" + verdict.outcome());
                };
        }

        // Never leave the campaign with zero active ads: pausing an ad/adset-level entity must not be the
        // last active unit. (A campaign-level pause is a deliberate whole-campaign stop, gated above.)
        boolean adOrAdset = target != null && (target.getAdId() != null || target.getAdSetId() != null);
        if (adOrAdset && state.activeUnitCount() <= 1L)
            return GuardrailVerdict.queue("would_leave_zero_active_ads");

        return null; // cleared the pause guardrails
    }

    // ---- budget guardrails -------------------------------------------------------------------

    private GuardrailVerdict checkBudget(ActionChange.BudgetShift shift, AutonomyPolicy policy, CampaignState state) {

        double current = amount(state.currentDailyBudget());
        double delta = amount(shift.amount());
        double newDaily = current + delta;

        // Floor: a run may never zero (or invert) the daily budget.
        if (newDaily <= 0.0d)
            return GuardrailVerdict.queue("below_budget_floor");

        // Max-change-per-run against the LIVE budget (falls back to the declared pctOfSource when the live
        // budget is unknown).
        if (policy.maxChangePerRunPct() > 0.0d) {
            double swingPct = current > 0.0d ? Math.abs(delta) / current * 100.0d : shift.pctOfSource() * 100.0d;
            if (swingPct > policy.maxChangePerRunPct())
                return GuardrailVerdict.queue("exceeds_max_change_per_run");
        }

        // Absolute daily cap.
        Money cap = policy.dailyBudgetCap();
        if (cap != null) {
            double capAmount = amount(cap);
            if (capAmount > 0.0d && newDaily > capAmount)
                return GuardrailVerdict.queue("exceeds_daily_budget_cap");
        }

        // Relative cap (multiple of current).
        if (policy.budgetCapFactor() > 0.0d && current > 0.0d && newDaily > current * policy.budgetCapFactor())
            return GuardrailVerdict.queue("exceeds_budget_cap_factor");

        return null; // cleared the budget guardrails
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static double amount(Money money) {
        return money == null || money.getAmount() == null ? 0.0d : money.getAmount().doubleValue();
    }

    private static GuardrailVerdict logged(Action action, GuardrailVerdict verdict) {
        if (!verdict.ok())
            logger.info("adzump.apply.guardrail campaign type={} outcome={} reason={}",
                    action.type(), verdict.outcome(), verdict.reason());
        return verdict;
    }
}
