package com.modlix.saas.adzump.service.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.ActionAuditDao;
import com.modlix.saas.adzump.dto.ActionAudit;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.campaign.CampaignService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.optimize.OptimizationEngine;
import com.modlix.saas.adzump.service.optimize.Risk;
import com.modlix.saas.adzump.service.optimize.SignificanceGate;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * J13 apply-layer unit tests, run entirely offline: the DAO / spine / feedback / autonomy collaborators
 * are mocked, while the real {@link AutonomyRouter} + {@link GuardrailEngine} + {@link SignificanceGate}
 * decide. Covers the kill-switch (nothing reaches the spine while disabled; an eligible action applies
 * once and is audited + snapshot-linked when enabled), guardrail downgrades on the apply path, the
 * "every decision is audited" invariant, and reversal.
 */
class ActionApplierTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final ULong PLAN_ID = ApplyFixtures.PLAN_ID;
    private static final ULong FRESH_SNAPSHOT_ID = ULong.valueOf(42);

    private CampaignPlanService campaignPlanService;
    private CampaignService campaignService;
    private FeedbackService feedbackService;
    private AutonomyConfigService autonomyConfigService;
    private OptimizationEngine optimizationEngine;
    private ActionAuditDao actionAuditDao;
    private FeignAuthenticationService security;

    private ActionApplier applier;

    private MockedStatic<SecurityContextUtil> securityCtx;
    private final AtomicLong idSeq = new AtomicLong(500);
    private final java.util.List<ActionAudit> created = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        this.campaignPlanService = mock(CampaignPlanService.class);
        this.campaignService = mock(CampaignService.class);
        this.feedbackService = mock(FeedbackService.class);
        this.autonomyConfigService = mock(AutonomyConfigService.class);
        this.optimizationEngine = mock(OptimizationEngine.class);
        this.actionAuditDao = mock(ActionAuditDao.class);
        this.security = mock(FeignAuthenticationService.class);

        this.applier = new ActionApplier(this.campaignPlanService, this.campaignService, this.feedbackService,
                this.autonomyConfigService, new AutonomyRouter(), new GuardrailEngine(new SignificanceGate()),
                this.optimizationEngine, this.actionAuditDao, this.security, MSG);

        ContextAuthentication ca = mock(ContextAuthentication.class);
        when(ca.getLoggedInFromClientCode()).thenReturn(ApplyFixtures.CLIENT);
        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(ca);

        // Assign an id to every created audit row and remember it, so a later read/reverse resolves it.
        when(this.actionAuditDao.create(any())).thenAnswer(inv -> {
            ActionAudit row = inv.getArgument(0);
            row.setId(ULong.valueOf(this.idSeq.getAndIncrement()));
            this.created.add(row);
            return row;
        });
        when(this.actionAuditDao.readById(any())).thenAnswer(inv -> {
            ULong id = inv.getArgument(0);
            return this.created.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        });

        // Default collaborators (individual tests override the autonomy config + snapshot as needed).
        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(ApplyFixtures.plan(50));
        when(this.feedbackService.readLatest(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.snapshot(LocalDateTime.now(), List.of()));
        when(this.feedbackService.getSnapshot(eq(PLAN_ID), any(), isNull())).thenReturn(freshSnapshot());
        when(this.autonomyConfigService.getEffective(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.hybrid(20.0d));
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // Kill-switch
    // =====================================================================================

    @Test
    void killSwitchDisabled_appliesNothingLive_queuesWithNote() {
        // applyLiveEnabled defaults false — no ReflectionTestUtils flip.
        AdGrainId target = ApplyFixtures.adSetGrain("C1", "AS1");
        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.negativeKeyword(target, Risk.LOW), "low_risk")));

        ApplyResult result = this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        // NOTHING reaches the one mutation spine.
        verify(this.campaignService, never()).editLive(any(), any(), any());
        verify(this.campaignService, never()).setStatus(any(), any(), any());

        // The action is QUEUED with the apply_live_disabled note; no re-snapshot / linkage.
        ActionAudit row = onlyCreated();
        assertEquals(AdzumpActionAuditVerdict.QUEUED, row.getVerdict());
        assertEquals(AdzumpMessageResourceService.APPLY_LIVE_DISABLED, row.getNotes());
        verify(this.feedbackService, never()).getSnapshot(any(), any(), any());
        verify(this.actionAuditDao, never()).linkSnapshot(anyList(), any());
        assertNull(result.newSnapshotId());
        assertEquals(0L, result.appliedCount());
    }

    @Test
    void killSwitchEnabled_appliesEligibleHybridAction_editLiveOnce_appliedAndSnapshotLinked() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);

        AdGrainId target = ApplyFixtures.adSetGrain("C1", "AS1");
        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.negativeKeyword(target, Risk.LOW), "low_risk")));

        ApplyResult result = this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        // Applied through the one spine, exactly once, via editLive (a structural lever).
        verify(this.campaignService, times(1)).editLive(eq(PLAN_ID), any(), isNull());
        verify(this.campaignService, never()).setStatus(any(), any(), any());

        ActionAudit row = onlyCreated();
        assertEquals(AdzumpActionAuditVerdict.APPLIED, row.getVerdict());

        // Fresh snapshot triggered and linked onto the applied row (§5.4 close-the-loop).
        verify(this.feedbackService).getSnapshot(eq(PLAN_ID), any(), isNull());
        ArgumentCaptor<List<ULong>> idsCap = idsCaptor();
        verify(this.actionAuditDao).linkSnapshot(idsCap.capture(), eq(FRESH_SNAPSHOT_ID));
        assertTrue(idsCap.getValue().contains(row.getId()));
        assertEquals(FRESH_SNAPSHOT_ID, result.newSnapshotId());
        assertEquals(1L, result.appliedCount());
    }

    // =====================================================================================
    // Guardrail downgrade on the apply path
    // =====================================================================================

    @Test
    void guardrailBreach_capExceedingBudgetShift_queuedNotApplied() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);
        when(this.autonomyConfigService.getEffective(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.autonomous(20.0d));

        // Router said APPLY, but +50 on a live 50 budget is a 100% swing > the 20% cap -> downgrade.
        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.budgetShift(ApplyFixtures.adGrain("C1", "A"),
                        ApplyFixtures.adSetGrain("C1", "AS1"), 50, 0.10, Risk.MED), "budget_within_cap")));

        this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        verify(this.campaignService, never()).editLive(any(), any(), any());
        ActionAudit row = onlyCreated();
        assertEquals(AdzumpActionAuditVerdict.QUEUED, row.getVerdict());
        assertEquals("exceeds_max_change_per_run", row.getNotes());
    }

    @Test
    void guardrailBreach_staleSnapshot_rejectedNotApplied() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);
        when(this.autonomyConfigService.getEffective(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.autonomy("AUTONOMOUS", 20.0d, null, 6L, 0L));
        when(this.feedbackService.readLatest(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.snapshot(LocalDateTime.now().minusHours(10), List.of()));

        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.negativeKeyword(ApplyFixtures.adSetGrain("C1", "AS1"),
                        Risk.LOW), "low_risk")));

        this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        verify(this.campaignService, never()).editLive(any(), any(), any());
        assertEquals(AdzumpActionAuditVerdict.REJECTED, onlyCreated().getVerdict());
    }

    @Test
    void guardrailBreach_pauseOnlyConverter_notApplied() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);
        AdGrainId target = ApplyFixtures.adGrain("C1", "AD_ONLY");
        when(this.feedbackService.readLatest(eq(PLAN_ID), isNull())).thenReturn(
                ApplyFixtures.snapshot(LocalDateTime.now(), List.of(ApplyFixtures.row(
                        com.modlix.saas.adzump.model.leadzump.Grain.AD, target, 8000, 300,
                        java.util.Map.of("lead", 20L, "qualified", 8L),
                        com.modlix.saas.adzump.model.snapshot.SignalMaturity.MATURE))));

        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.pause(target, true, Risk.HIGH), "kill")));

        this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        verify(this.campaignService, never()).setStatus(any(), any(), any());
        assertEquals(AdzumpActionAuditVerdict.SUPPRESSED, onlyCreated().getVerdict());
    }

    // =====================================================================================
    // Every decision is audited
    // =====================================================================================

    @Test
    void everyDecisionIsAudited_appliedQueuedSuppressed() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);

        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID, List.of(
                RoutedAction.apply(ApplyFixtures.negativeKeyword(ApplyFixtures.adSetGrain("C1", "AS1"), Risk.LOW),
                        "low_risk"),
                RoutedAction.queue(ApplyFixtures.bidChange(ApplyFixtures.adSetGrain("C1", "AS1"), Risk.MED),
                        "bid_requires_approval"),
                RoutedAction.suppress(ApplyFixtures.requestVariant(ApplyFixtures.adGrain("C1", "AD1"), Risk.LOW),
                        "routed_to_experiment")));

        ApplyResult result = this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);

        // One audit row per decision, in order.
        ArgumentCaptor<ActionAudit> cap = ArgumentCaptor.forClass(ActionAudit.class);
        verify(this.actionAuditDao, times(3)).create(cap.capture());
        List<ActionAudit> rows = cap.getAllValues();
        assertEquals(AdzumpActionAuditVerdict.APPLIED, rows.get(0).getVerdict());
        assertEquals(AdzumpActionAuditVerdict.QUEUED, rows.get(1).getVerdict());
        assertEquals(AdzumpActionAuditVerdict.SUPPRESSED, rows.get(2).getVerdict());

        // Only the applied one touched the spine.
        verify(this.campaignService, times(1)).editLive(eq(PLAN_ID), any(), isNull());
        assertEquals(3, result.decisions().size());
    }

    // =====================================================================================
    // Reversal
    // =====================================================================================

    @Test
    void reverse_restoresPriorBudget_throughSpine_writesReversedRow() {
        ReflectionTestUtils.setField(this.applier, "applyLiveEnabled", true);
        when(this.autonomyConfigService.getEffective(eq(PLAN_ID), isNull()))
                .thenReturn(ApplyFixtures.autonomous(20.0d));

        // Apply a small budget shift: live 50 -> 55 (a 10% swing, within the 20% cap).
        ApplyPlan plan = new ApplyPlan(PLAN_ID, ApplyFixtures.SNAPSHOT_ID,
                List.of(RoutedAction.apply(ApplyFixtures.budgetShift(ApplyFixtures.adGrain("C1", "A"),
                        ApplyFixtures.adSetGrain("C1", "AS1"), 5, 0.10, Risk.MED), "budget_within_cap")));

        ApplyResult applyResult = this.applier.apply(PLAN_ID, plan, AdzumpActionAuditTriggeredBy.AGENT, null);
        ULong appliedAuditId = applyResult.decisions().get(0).auditId();
        assertEquals(AdzumpActionAuditVerdict.APPLIED, applyResult.decisions().get(0).verdict());

        // Reverse it: restore the prior budget through the same spine, and record a REVERSED row.
        ApplyDecision reversal = this.applier.reverse(appliedAuditId, null);
        assertEquals(AdzumpActionAuditVerdict.REVERSED, reversal.verdict());

        // editLive called twice: once to apply (-> 55), once to reverse (-> the prior 50).
        ArgumentCaptor<JsonNode> patchCap = ArgumentCaptor.forClass(JsonNode.class);
        verify(this.campaignService, times(2)).editLive(eq(PLAN_ID), patchCap.capture(), isNull());
        double appliedAmount = patchCap.getAllValues().get(0).at("/budget/dailyBudget/amount").asDouble();
        double reversedAmount = patchCap.getAllValues().get(1).at("/budget/dailyBudget/amount").asDouble();
        assertEquals(55.0d, appliedAmount, 0.0001d);
        assertEquals(50.0d, reversedAmount, 0.0001d);

        assertEquals(AdzumpActionAuditVerdict.REVERSED, lastCreated().getVerdict());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private ActionAudit onlyCreated() {
        assertEquals(1, this.created.size(), "expected exactly one audit row");
        return this.created.get(0);
    }

    private ActionAudit lastCreated() {
        return this.created.get(this.created.size() - 1);
    }

    private static PerformanceSnapshot freshSnapshot() {
        PerformanceSnapshot fresh = ApplyFixtures.snapshot(LocalDateTime.now(), List.of());
        fresh.setId(FRESH_SNAPSHOT_ID);
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<ULong>> idsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
