package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PerformancePolicyService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.feedback.PolicyScorer;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict.GateOutcome;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Offline, seeded-fixture tests for the J12 {@link OptimizationEngine} exit criteria: on a seeded
 * underperformer it proposes the correct actions (shift budget off the loser, negative-keyword the
 * waste, pause the zero-outcome ad) ranked by objective delta; a noise fixture yields NO action (the
 * gate holds, explainably); a slow-converting winner on thin FAST_ONLY data is NOT proposed for kill;
 * the per-run cap defers overflow; and nothing auto-applies (every action requires approval). The J10
 * feedback service + config services are mocked; the analyzers, gate, and objective are real.
 */
class OptimizationEngineTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private FeedbackService feedback;
    private CampaignPlanService planService;
    private PerformancePolicyService policyService;
    private AutonomyConfigService autonomyService;
    private FeignAuthenticationService security;
    private OptimizationEngine engine;

    @BeforeEach
    void setUp() {
        this.feedback = mock(FeedbackService.class);
        this.planService = mock(CampaignPlanService.class);
        this.policyService = mock(PerformancePolicyService.class);
        this.autonomyService = mock(AutonomyConfigService.class);
        this.security = mock(FeignAuthenticationService.class);

        List<DimensionAnalyzer> analyzers = List.of(
                new BudgetAnalyzer(),
                new BidAnalyzer(),
                new AudienceAnalyzer(),
                new KeywordAnalyzer(),
                new CreativeAnalyzer(new HeuristicCreativeScoreProvider()));

        this.engine = new OptimizationEngine(analyzers, new SignificanceGate(), new Objective(new PolicyScorer()),
                this.feedback, this.planService, this.policyService, this.autonomyService, this.security, MSG);
    }

    private void stubReads(PerformanceSnapshot snap, CampaignPlan plan, PerformancePolicy policy,
            AutonomyConfig autonomy) {
        when(this.feedback.readLatest(OptimizeFixtures.PLAN_ID, null)).thenReturn(snap);
        when(this.planService.read(OptimizeFixtures.PLAN_ID)).thenReturn(plan);
        when(this.policyService.getEffective(OptimizeFixtures.PLAN_ID, null)).thenReturn(policy);
        when(this.autonomyService.getEffective(OptimizeFixtures.PLAN_ID, null)).thenReturn(autonomy);
    }

    // =====================================================================================

    @Test
    void underperformer_proposesCorrectActions_rankedByDelta_nothingApplied() {

        stubReads(OptimizeFixtures.underperformer(), OptimizeFixtures.googlePlan(), OptimizeFixtures.policy(), null);

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID);

        assertEquals(3, set.actions().size());

        // The three expected dimensions all fired.
        List<AdzumpActionAuditActionType> types = set.actions().stream().map(Action::type).toList();
        assertTrue(types.contains(AdzumpActionAuditActionType.SHIFT_BUDGET));
        assertTrue(types.contains(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD));
        assertTrue(types.contains(AdzumpActionAuditActionType.PAUSE_ENTITY));

        // Ranked by objective delta descending: pause the dead ad (10) > shift budget (8) > negative kw (3).
        assertEquals(AdzumpActionAuditActionType.PAUSE_ENTITY, set.actions().get(0).type());
        assertEquals(AdzumpActionAuditActionType.SHIFT_BUDGET, set.actions().get(1).type());
        assertEquals(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD, set.actions().get(2).type());
        assertDescendingByDelta(set);

        // Recommend-mode: EVERYTHING requires approval; nothing auto-applies.
        assertTrue(set.actions().stream().allMatch(Action::requiresApproval));

        // Objective wraps the PolicyScorer rollup; projected-after = before + summed heuristic deltas.
        assertEquals(40.0d, set.objectiveBefore(), 1e-9);
        assertEquals(61.0d, set.objectiveProjectedAfter(), 1e-9);
        assertEquals(9L, set.snapshotId().longValue());
    }

    @Test
    void noise_yieldsNoAction_gateHolds_butExplainable() {

        stubReads(OptimizeFixtures.noise(), OptimizeFixtures.googlePlan(), OptimizeFixtures.policy(), null);

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID);

        assertTrue(set.actions().isEmpty(), "the gate must hold on thin data");
        assertFalse(set.suppressed().isEmpty(), "suppressions are logged, not silent");
        assertTrue(set.suppressed().stream().anyMatch(s -> s.verdict().outcome() == GateOutcome.MIN_VOLUME));
        // No action means the objective is projected unchanged.
        assertEquals(set.objectiveBefore(), set.objectiveProjectedAfter(), 1e-9);
    }

    @Test
    void slowConvertingWinner_onThinFastData_isNotProposedForKill() {

        stubReads(OptimizeFixtures.slowConverter(), OptimizeFixtures.googlePlan(), OptimizeFixtures.policy(), null);

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID);

        // No pause/kill of the slow-converting AD survives the maturity gate.
        boolean killProposed = set.actions().stream().anyMatch(
                a -> a.type() == AdzumpActionAuditActionType.PAUSE_ENTITY && "AD_SLOW".equals(a.target().getAdId()));
        assertFalse(killProposed, "a slow-converting potential winner must not be killed on thin fast data");

        // ...and the suppression is explainable: IMMATURE_SIGNAL on AD_SLOW.
        assertTrue(set.suppressed().stream().anyMatch(s -> "AD_SLOW".equals(s.target().getAdId())
                && s.verdict().outcome() == GateOutcome.IMMATURE_SIGNAL));

        assertTrue(set.actions().stream().allMatch(Action::requiresApproval));
    }

    @Test
    void maxChangesPerRun_capsProposals_overflowDeferred() {

        stubReads(OptimizeFixtures.underperformer(), OptimizeFixtures.googlePlan(), OptimizeFixtures.policy(),
                OptimizeFixtures.autonomyWithMaxChanges(2));

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID);

        // Top two by delta survive; the third (negative keyword, delta 3) is deferred, not dropped.
        assertEquals(2, set.actions().size());
        assertTrue(set.suppressed().stream().anyMatch(s -> s.verdict().outcome() == GateOutcome.MAX_CHANGES_EXCEEDED));
    }

    @Test
    void getRecommendations_withWindow_readsWindowScopedSnapshot_andBuildsTheActionSet() {

        SnapshotWindow window = new SnapshotWindow()
                .setFrom(LocalDate.of(2026, 6, 1)).setTo(LocalDate.of(2026, 6, 30));

        // The window overload reads the window-scoped snapshot; plan/policy/autonomy resolve as usual.
        stubReads(OptimizeFixtures.underperformer(), OptimizeFixtures.googlePlan(), OptimizeFixtures.policy(), null);
        when(this.feedback.readLatest(eq(OptimizeFixtures.PLAN_ID), eq(window), eq((String) null)))
                .thenReturn(OptimizeFixtures.underperformer());

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID, window, null);

        // Same three ranked, recommend-only actions as the no-window read (window only scopes the snapshot).
        assertEquals(3, set.actions().size());
        assertTrue(set.actions().stream().allMatch(Action::requiresApproval));
        verify(this.feedback).readLatest(eq(OptimizeFixtures.PLAN_ID), eq(window), eq((String) null));
    }

    @Test
    void empty_whenNoSnapshotBuiltYet() {

        when(this.feedback.readLatest(OptimizeFixtures.PLAN_ID, null)).thenReturn(null);

        ActionSet set = this.engine.getRecommendations(OptimizeFixtures.PLAN_ID);

        assertTrue(set.actions().isEmpty());
        assertTrue(set.suppressed().isEmpty());
        assertNull(set.snapshotId());
        assertEquals(0.0d, set.objectiveBefore(), 1e-9);
        verify(this.planService, never()).read(any());
    }

    @Test
    void optimize_crossClientDeny_forbiddenBeforeAnyBuild() {

        ContextAuthentication ca = mock(ContextAuthentication.class);
        when(ca.getLoggedInFromClientCode()).thenReturn("CLI0");
        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(ca.getUser()).thenReturn(user);
        when(ca.getUrlAppCode()).thenReturn("adzump");
        when(ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        try (MockedStatic<SecurityContextUtil> ctx = Mockito.mockStatic(SecurityContextUtil.class)) {
            ctx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(ca);

            assertThrows(GenericException.class,
                    () -> this.engine.optimize(OptimizeFixtures.PLAN_ID, null, "OTHER"));
        }

        // Denied at effective-client resolution, before any snapshot read/build.
        verify(this.feedback, never()).readLatest(any(), any());
        verify(this.feedback, never()).getSnapshot(any(), any(), any());
    }

    // =====================================================================================

    private static void assertDescendingByDelta(ActionSet set) {
        List<Action> actions = set.actions();
        for (int i = 1; i < actions.size(); i++)
            assertTrue(actions.get(i - 1).expectedDelta() >= actions.get(i).expectedDelta(),
                    "actions must be ranked by expected objective delta (descending)");
    }
}
