package com.modlix.saas.adzump.service.apply;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.enums.AutonomyMode;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionChange;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.Risk;

/**
 * J13 §5.1 — autonomy routing. Turns an {@link ActionSet} into an {@link ApplyPlan} by deciding, per
 * action, whether the campaign's {@link AutonomyMode mode} lets it apply now ({@link ApplyRoute#APPLY}),
 * must queue for a human ({@link ApplyRoute#QUEUE}), or is not actionable on the apply path
 * ({@link ApplyRoute#SUPPRESS}).
 *
 * <p>Routing is a <b>declared-intent</b> decision: it reads the action's {@link Risk}, its type, and the
 * declared {@link ActionChange} (e.g. a budget shift's {@code pctOfSource}, a pause's {@code kill} flag)
 * against the {@link AutonomyPolicy}'s caps. It deliberately does <b>not</b> look at live campaign state
 * — that re-check (do-no-harm, live budget, staleness, rate) is the {@link GuardrailEngine}'s job at apply
 * time, and can still downgrade an {@code APPLY} here. Pure and deterministic; no I/O, no authority (the
 * caller — {@link ActionApplier} — holds the EDIT gate).
 *
 * <p>The per-mode rules (§5.1):
 * <ul>
 * <li><b>RECOMMEND</b> — everything QUEUE (a human approves each). The P3 behavior, still valid per
 * campaign.</li>
 * <li><b>HYBRID</b> — APPLY the low-risk set (add-negative-keyword, a budget shift within
 * {@code maxChangePerRunPct}, a pause of a clear zero-outcome loser); QUEUE the rest (kills, big budget
 * moves, bid/audience/creative changes, anything HIGH risk).</li>
 * <li><b>AUTONOMOUS</b> — APPLY within caps; QUEUE only what exceeds a cap or is HIGH risk.</li>
 * </ul>
 * {@code REQUEST_VARIANT} is <b>never</b> an apply in any mode — it is routed out to the creative
 * experiment path (SUPPRESS on the apply path).
 */
@Service
public class AutonomyRouter {

    /**
     * Routes every action in {@code actions} under {@code policy}. Returns an {@link ApplyPlan} preserving
     * the ActionSet's ranked order. A {@code null}/empty ActionSet yields an empty plan.
     */
    public ApplyPlan route(ActionSet actions, AutonomyPolicy policy) {

        List<RoutedAction> routed = new ArrayList<>();
        if (actions != null && actions.actions() != null)
            for (Action action : actions.actions())
                routed.add(routeOne(action, policy));

        return new ApplyPlan(actions == null ? null : actions.campaignPlanId(),
                actions == null ? null : actions.snapshotId(), List.copyOf(routed));
    }

    /** Routes a single action; visible for the {@code propose_action}/approve single-action paths. */
    public RoutedAction routeOne(Action action, AutonomyPolicy policy) {

        AutonomyMode mode = policy == null ? AutonomyMode.RECOMMEND : policy.mode();

        // REQUEST_VARIANT is never applied through the mutation spine — it is a creative-generation ask,
        // handed to the experiment path (A4/J21), not a platform mutation.
        if (action.type() == com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.REQUEST_VARIANT)
            return RoutedAction.suppress(action, "request_variant_routed_to_experiment");

        return switch (mode) {
            case RECOMMEND -> RoutedAction.queue(action, "recommend_mode");
            case HYBRID -> routeHybrid(action, policy);
            case AUTONOMOUS -> routeAutonomous(action, policy);
        };
    }

    // HYBRID: apply the explicitly-safe low-risk set; queue everything else.
    private RoutedAction routeHybrid(Action action, AutonomyPolicy policy) {

        if (action.risk() == Risk.HIGH)
            return RoutedAction.queue(action, "high_risk_requires_approval");

        return switch (action.type()) {
            case ADD_NEGATIVE_KEYWORD -> RoutedAction.apply(action, "low_risk_negative_keyword");
            case SHIFT_BUDGET -> isSmallBudgetShift(action.change(), policy)
                    ? RoutedAction.apply(action, "budget_shift_within_cap")
                    : RoutedAction.queue(action, "budget_shift_exceeds_cap");
            case PAUSE_ENTITY -> isClearLoserPause(action.change())
                    ? RoutedAction.apply(action, "pause_zero_outcome_loser")
                    : RoutedAction.queue(action, "kill_requires_approval");
            case ADJUST_BID -> RoutedAction.queue(action, "bid_change_requires_approval");
            case REFINE_AUDIENCE -> RoutedAction.queue(action, "audience_change_requires_approval");
            case ROTATE_CREATIVE -> RoutedAction.queue(action, "creative_change_requires_approval");
            case REQUEST_VARIANT -> RoutedAction.suppress(action, "request_variant_routed_to_experiment");
        };
    }

    // AUTONOMOUS: apply within caps; queue only cap-exceeding moves and HIGH risk.
    private RoutedAction routeAutonomous(Action action, AutonomyPolicy policy) {

        if (action.risk() == Risk.HIGH)
            return RoutedAction.queue(action, "high_risk_requires_approval");

        return switch (action.type()) {
            case SHIFT_BUDGET -> isSmallBudgetShift(action.change(), policy)
                    ? RoutedAction.apply(action, "budget_shift_within_cap")
                    : RoutedAction.queue(action, "budget_shift_exceeds_cap");
            case REQUEST_VARIANT -> RoutedAction.suppress(action, "request_variant_routed_to_experiment");
            default -> RoutedAction.apply(action, "autonomous_within_caps");
        };
    }

    /**
     * A budget shift is "small" when its declared {@code pctOfSource} is within the policy's
     * {@code maxChangePerRunPct} (an unbounded cap admits any shift). The live-budget re-check against the
     * campaign's current daily budget is the {@link GuardrailEngine}'s job at apply time.
     */
    private static boolean isSmallBudgetShift(ActionChange change, AutonomyPolicy policy) {
        if (!(change instanceof ActionChange.BudgetShift shift))
            return false;
        double cap = policy == null ? 0.0d : policy.maxChangePerRunPct();
        if (cap <= 0.0d)
            return true; // unbounded cap
        return shift.pctOfSource() * 100.0d <= cap;
    }

    /** A "clear loser" pause is a trim of zero-outcome waste ({@code kill == false}), not a converter kill. */
    private static boolean isClearLoserPause(ActionChange change) {
        return change instanceof ActionChange.Pause pause && !pause.kill();
    }
}
