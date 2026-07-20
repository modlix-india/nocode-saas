package com.modlix.saas.adzump.service.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.AutonomyMode;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.Risk;

/**
 * J13 §5.1 routing matrix (mode × risk/type → APPLY/QUEUE/SUPPRESS). Pure — the router reads declared
 * intent (type, risk, declared caps) only; no live state, no mocks.
 */
class AutonomyRouterTest {

    private final AutonomyRouter router = new AutonomyRouter();

    private static final AutonomyPolicy RECOMMEND = AutonomyPolicy.conservative();
    private static final AutonomyPolicy HYBRID = AutonomyPolicy.from(ApplyFixtures.hybrid(20.0d));
    private static final AutonomyPolicy AUTONOMOUS = AutonomyPolicy.from(ApplyFixtures.autonomous(20.0d));

    private static final AdGrainId AD = ApplyFixtures.adGrain("C1", "AD1");
    private static final AdGrainId ADSET = ApplyFixtures.adSetGrain("C1", "AS1");

    private ApplyRoute route(Action action, AutonomyPolicy policy) {
        return this.router.routeOne(action, policy).route();
    }

    // ---- RECOMMEND: everything queues --------------------------------------------------------

    @Test
    void recommendMode_queuesEveryAction() {
        assertEquals(AutonomyMode.RECOMMEND, RECOMMEND.mode());
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.negativeKeyword(ADSET, Risk.LOW), RECOMMEND));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.budgetShift(AD, ADSET, 5, 0.05, Risk.LOW), RECOMMEND));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.pause(AD, false, Risk.MED), RECOMMEND));
        // REQUEST_VARIANT is never an apply, in any mode.
        assertEquals(ApplyRoute.SUPPRESS, route(ApplyFixtures.requestVariant(AD, Risk.LOW), RECOMMEND));
    }

    // ---- HYBRID: apply the low-risk set; queue the rest --------------------------------------

    @Test
    void hybridMode_appliesLowRiskSet() {
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.negativeKeyword(ADSET, Risk.LOW), HYBRID));
        // small budget shift within the cap (10% <= 20%)
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.budgetShift(AD, ADSET, 5, 0.10, Risk.MED), HYBRID));
        // pause of a clear zero-outcome loser (kill == false)
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.pause(AD, false, Risk.MED), HYBRID));
    }

    @Test
    void hybridMode_queuesKillsBigMovesAndComplexChanges() {
        // big budget shift over the cap (50% > 20%)
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.budgetShift(AD, ADSET, 50, 0.50, Risk.MED), HYBRID));
        // a kill (pause of a converter)
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.pause(AD, true, Risk.HIGH), HYBRID));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.bidChange(ADSET, Risk.MED), HYBRID));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.refineAudience(ADSET, Risk.MED), HYBRID));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.rotateCreative(AD, Risk.MED), HYBRID));
    }

    @Test
    void hybridMode_queuesAnythingHighRisk() {
        // even the normally-applied negative keyword queues when marked HIGH risk
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.negativeKeyword(ADSET, Risk.HIGH), HYBRID));
    }

    @Test
    void hybridMode_routesVariantOut() {
        assertEquals(ApplyRoute.SUPPRESS, route(ApplyFixtures.requestVariant(AD, Risk.LOW), HYBRID));
    }

    // ---- AUTONOMOUS: apply within caps; queue only cap-exceeding or HIGH risk -----------------

    @Test
    void autonomousMode_appliesWithinCaps() {
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.negativeKeyword(ADSET, Risk.LOW), AUTONOMOUS));
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.budgetShift(AD, ADSET, 5, 0.10, Risk.MED), AUTONOMOUS));
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.pause(AD, false, Risk.MED), AUTONOMOUS));
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.bidChange(ADSET, Risk.MED), AUTONOMOUS));
        assertEquals(ApplyRoute.APPLY, route(ApplyFixtures.refineAudience(ADSET, Risk.MED), AUTONOMOUS));
    }

    @Test
    void autonomousMode_queuesCapExceedingAndHighRisk() {
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.budgetShift(AD, ADSET, 50, 0.50, Risk.MED), AUTONOMOUS));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.pause(AD, true, Risk.HIGH), AUTONOMOUS));
        assertEquals(ApplyRoute.QUEUE, route(ApplyFixtures.bidChange(ADSET, Risk.HIGH), AUTONOMOUS));
    }

    @Test
    void autonomousMode_routesVariantOut() {
        assertEquals(ApplyRoute.SUPPRESS, route(ApplyFixtures.requestVariant(AD, Risk.LOW), AUTONOMOUS));
    }

    // ---- route(ActionSet) preserves order + carries the snapshot id --------------------------

    @Test
    void routeActionSet_producesPlanPerActionInOrder() {
        ActionSet set = ApplyFixtures.actionSet(List.of(
                ApplyFixtures.negativeKeyword(ADSET, Risk.LOW),
                ApplyFixtures.bidChange(ADSET, Risk.MED),
                ApplyFixtures.requestVariant(AD, Risk.LOW)));

        ApplyPlan plan = this.router.route(set, HYBRID);

        assertEquals(ApplyFixtures.PLAN_ID, plan.campaignPlanId());
        assertEquals(ApplyFixtures.SNAPSHOT_ID, plan.snapshotId());
        assertEquals(3, plan.routed().size());
        assertEquals(ApplyRoute.APPLY, plan.routed().get(0).route());
        assertEquals(ApplyRoute.QUEUE, plan.routed().get(1).route());
        assertEquals(ApplyRoute.SUPPRESS, plan.routed().get(2).route());
        assertEquals(1L, plan.applyCount());
    }
}
