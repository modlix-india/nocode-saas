package com.modlix.saas.adzump.service.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dao.CampaignPlanDao;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.Cadence;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ApplyDecision;
import com.modlix.saas.adzump.service.apply.ApplyResult;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * J14 scheduling unit tests, entirely offline: the campaign load (DAO), feedback (J10), apply (J13),
 * and autonomy collaborators are mocked; the real {@link ServiceTokenMinter} mints the campaign-scoped
 * context. Covers the internal loop (snapshot → applyLatest as SCHEDULER, in the account tz), the
 * principal-C scope guard (a context for campaign A cannot drive campaign B / another client), on-demand
 * delegating to the same loop path, the per-campaign run lock serializing an overlapping fire, an
 * idempotent re-fire not double-applying, and cadence parsed from the autonomy body.
 */
class ScheduleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private static final ULong CID = ULong.valueOf(100);
    private static final ULong OTHER = ULong.valueOf(200);
    private static final String CLIENT = "CLI0";
    private static final String OTHER_CLIENT = "CLI9";
    private static final String TZ = "Asia/Kolkata";

    private CampaignPlanDao campaignPlanDao;
    private CampaignPlanService campaignPlanService;
    private FeedbackService feedbackService;
    private com.modlix.saas.adzump.service.apply.ActionApplier actionApplier;
    private AutonomyConfigService autonomyConfigService;
    private FeignAuthenticationService security;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        this.campaignPlanDao = mock(CampaignPlanDao.class);
        this.campaignPlanService = mock(CampaignPlanService.class);
        this.feedbackService = mock(FeedbackService.class);
        this.actionApplier = mock(com.modlix.saas.adzump.service.apply.ActionApplier.class);
        this.autonomyConfigService = mock(AutonomyConfigService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.service = new ScheduleService(this.campaignPlanDao, this.campaignPlanService,
                new ServiceTokenMinter(), this.feedbackService, this.actionApplier, this.autonomyConfigService,
                this.security, MSG);
    }

    // =====================================================================================
    // Internal loop: snapshot (account-tz yesterday) -> applyLatest as SCHEDULER
    // =====================================================================================

    @Test
    void internalRun_snapshotThenApplyLatestAsScheduler_inAccountTz_countsSurfaced() {
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenReturn(snapshot(ULong.valueOf(7)));
        when(this.actionApplier.applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT)))
                .thenReturn(applyResult(1, 2, 1));

        OptimizeRun run = this.service.optimize(CID, null);

        // The J10 snapshot runs over "yesterday" in the ACCOUNT reporting timezone (DESIGN §9.5).
        ArgumentCaptor<SnapshotWindow> windowCap = ArgumentCaptor.forClass(SnapshotWindow.class);
        verify(this.feedbackService).getSnapshot(eq(CID), windowCap.capture(), eq(CLIENT));
        LocalDate expectedYesterday = LocalDate.now(ZoneId.of(TZ)).minusDays(1);
        assertEquals(expectedYesterday, windowCap.getValue().getFrom());
        assertEquals(expectedYesterday, windowCap.getValue().getTo());
        assertEquals("Asia/Kolkata", windowCap.getValue().getTimezone());

        // Snapshot is built BEFORE apply (the loop diagnoses then acts), and apply is headless as SCHEDULER.
        InOrder inOrder = Mockito.inOrder(this.feedbackService, this.actionApplier);
        inOrder.verify(this.feedbackService).getSnapshot(eq(CID), any(), eq(CLIENT));
        inOrder.verify(this.actionApplier).applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER),
                eq(CLIENT));

        assertEquals(CID, run.campaignId());
        assertEquals(ULong.valueOf(7), run.snapshotId());
        assertEquals(1, run.appliedCount());
        assertEquals(2, run.queuedCount());
        assertEquals(1, run.suppressedCount());
        assertNotNull(run.runId());
    }

    @Test
    void internalRun_missingScheduleTz_fallsBackToUtc() {
        stubCampaign(campaign(CID, CLIENT, null)); // no schedule / no tz
        when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenReturn(snapshot(ULong.valueOf(7)));
        when(this.actionApplier.applyLatest(eq(CID), any(), eq(CLIENT))).thenReturn(applyResult(0, 0, 0));

        this.service.optimize(CID, null);

        ArgumentCaptor<SnapshotWindow> windowCap = ArgumentCaptor.forClass(SnapshotWindow.class);
        verify(this.feedbackService).getSnapshot(eq(CID), windowCap.capture(), eq(CLIENT));
        assertEquals("Z", windowCap.getValue().getTimezone()); // ZoneOffset.UTC id
        assertEquals(LocalDate.now(ZoneId.of("UTC")).minusDays(1), windowCap.getValue().getFrom());
    }

    // =====================================================================================
    // Principal C: the scoped context can only drive its own campaign / client
    // =====================================================================================

    @Test
    void scopedContext_forCampaignA_cannotDriveCampaignB_denied() {
        ScopedContext ctxA = new ScopedContext(CLIENT, CID, ScopedContext.CAMPAIGN_OPTIMIZE);

        assertThrows(GenericException.class, () -> this.service.optimize(ctxA, OTHER, null));

        // Denied before any load / snapshot / apply — a run cannot touch another campaign's client.
        verify(this.campaignPlanDao, never()).readAll(any());
        verify(this.feedbackService, never()).getSnapshot(any(), any(), any());
        verify(this.actionApplier, never()).applyLatest(any(), any(), any());
    }

    @Test
    void scopedContext_clientMismatch_denied() {
        // Context claims campaign CID but a different client than the campaign actually belongs to.
        ScopedContext forged = new ScopedContext(OTHER_CLIENT, CID, ScopedContext.CAMPAIGN_OPTIMIZE);
        stubCampaign(campaign(CID, CLIENT, TZ));

        assertThrows(GenericException.class, () -> this.service.optimize(forged, CID, null));

        verify(this.feedbackService, never()).getSnapshot(any(), any(), any());
        verify(this.actionApplier, never()).applyLatest(any(), any(), any());
    }

    @Test
    void scopedContext_matchingCampaignAndClient_runs() {
        ScopedContext ctxA = new ScopedContext(CLIENT, CID, ScopedContext.CAMPAIGN_OPTIMIZE);
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenReturn(snapshot(ULong.valueOf(7)));
        when(this.actionApplier.applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT)))
                .thenReturn(applyResult(1, 0, 0));

        OptimizeRun run = this.service.optimize(ctxA, CID, null);

        assertEquals(1, run.appliedCount());
        verify(this.actionApplier).applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT));
    }

    // =====================================================================================
    // On-demand ("run now") = same loop-execution path
    // =====================================================================================

    @Test
    void onDemand_delegatesToSameLoopPath() {
        try (MockedStatic<SecurityContextUtil> ctx = Mockito.mockStatic(SecurityContextUtil.class)) {
            ContextAuthentication ca = mock(ContextAuthentication.class);
            when(ca.getLoggedInFromClientCode()).thenReturn(CLIENT);
            ctx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(ca);

            when(this.campaignPlanService.read(CID)).thenReturn(campaign(CID, CLIENT, TZ));
            when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenReturn(snapshot(ULong.valueOf(7)));
            when(this.actionApplier.applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT)))
                    .thenReturn(applyResult(1, 0, 0));

            OptimizeRun run = this.service.optimizeOnDemand(CID, null, null);

            // Human path runs the managed-client gated read, then the SAME loop (snapshot -> applyLatest).
            verify(this.campaignPlanService).read(CID);
            verify(this.feedbackService).getSnapshot(eq(CID), any(), eq(CLIENT));
            verify(this.actionApplier).applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT));
            assertEquals(1, run.appliedCount());
        }
    }

    // =====================================================================================
    // Run lock: overlapping fires for one campaign are serialized
    // =====================================================================================

    @Test
    void runLock_serializesOverlappingFireForSameCampaign() throws Exception {
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.actionApplier.applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT)))
                .thenReturn(applyResult(0, 0, 0));

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger snapCalls = new AtomicInteger();
        when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenAnswer(inv -> {
            if (snapCalls.incrementAndGet() == 1) {
                inside.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS), "release latch timed out");
            }
            return snapshot(ULong.valueOf(7));
        });

        AtomicReference<Throwable> errA = new AtomicReference<>();
        AtomicReference<Throwable> errB = new AtomicReference<>();
        Thread a = daemon(() -> this.service.optimize(CID, null), errA);
        a.start();
        assertTrue(inside.await(3, TimeUnit.SECONDS), "thread A never entered the lock");

        AtomicBoolean bDone = new AtomicBoolean(false);
        Thread b = daemon(() -> {
            this.service.optimize(CID, null);
            bDone.set(true);
        }, errB);
        b.start();

        // While A holds the per-campaign lock, B must be blocked BEFORE getSnapshot (serialized).
        Thread.sleep(300);
        assertEquals(1, snapCalls.get(), "B entered the loop concurrently — the run lock did not serialize");
        assertFalse(bDone.get());

        release.countDown();
        a.join(3000);
        b.join(3000);

        assertNull(errA.get());
        assertNull(errB.get());
        assertEquals(2, snapCalls.get());
        assertTrue(bDone.get());
        verify(this.actionApplier, times(2)).applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER),
                eq(CLIENT));
    }

    // =====================================================================================
    // Idempotency: a re-fire for the same window does not double-apply (J13 audit dedupes)
    // =====================================================================================

    @Test
    void idempotentReFire_sameWindow_doesNotDoubleApply() {
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.feedbackService.getSnapshot(eq(CID), any(), eq(CLIENT))).thenReturn(snapshot(ULong.valueOf(7)));
        // J13 audit dedupe: the first fire applies; a re-fire for the same window finds the ActionSet
        // already applied and applies nothing new.
        when(this.actionApplier.applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER), eq(CLIENT)))
                .thenReturn(applyResult(1, 0, 0), applyResult(0, 1, 0));

        SnapshotWindow window = new SnapshotWindow()
                .setFrom(LocalDate.now().minusDays(1)).setTo(LocalDate.now().minusDays(1)).setTimezone(TZ);

        OptimizeRun first = this.service.optimize(CID, window);
        OptimizeRun again = this.service.optimize(CID, window);

        assertEquals(1, first.appliedCount());
        assertEquals(0, again.appliedCount());
        // Both re-fires funnel through the ONE apply path (J14 adds no divergent apply of its own).
        verify(this.actionApplier, times(2)).applyLatest(eq(CID), eq(AdzumpActionAuditTriggeredBy.SCHEDULER),
                eq(CLIENT));
    }

    // =====================================================================================
    // Cadence registration seam
    // =====================================================================================

    @Test
    void cadenceFor_parsesFromAutonomyBody() {
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.autonomyConfigService.getEffective(eq(CID), eq(CLIENT)))
                .thenReturn(autonomyWithCadence("TWICE_DAILY"));

        assertEquals(Cadence.TWICE_DAILY, this.service.cadenceFor(CID));
    }

    @Test
    void cadenceFor_defaultsToOnDemandWhenUnconfigured() {
        stubCampaign(campaign(CID, CLIENT, TZ));
        when(this.autonomyConfigService.getEffective(eq(CID), eq(CLIENT)))
                .thenReturn(new AutonomyConfig().setBody(MAPPER.createObjectNode()));

        assertEquals(Cadence.ON_DEMAND, this.service.cadenceFor(CID));
    }

    // =====================================================================================
    // Fixtures
    // =====================================================================================

    private void stubCampaign(CampaignPlan campaign) {
        when(this.campaignPlanDao.readAll(any())).thenReturn(List.of(campaign));
    }

    private static CampaignPlan campaign(ULong id, String client, String tz) {
        CampaignPlanBody body = new CampaignPlanBody();
        if (tz != null)
            body.setSchedule(new ScheduleConfig().setTimezone(tz).setOptimizationCadence(Cadence.DAILY));
        CampaignPlan plan = new CampaignPlan().setClientCode(client);
        plan.setId(id);
        plan.setBody(body);
        return plan;
    }

    private static PerformanceSnapshot snapshot(ULong id) {
        PerformanceSnapshot snap = new PerformanceSnapshot().setClientCode(CLIENT).setCampaignPlanId(CID);
        snap.setId(id);
        return snap;
    }

    private static ApplyResult applyResult(int applied, int queued, int suppressed) {
        List<ApplyDecision> decisions = new ArrayList<>();
        for (int i = 0; i < applied; i++)
            decisions.add(decision(AdzumpActionAuditVerdict.APPLIED));
        for (int i = 0; i < queued; i++)
            decisions.add(decision(AdzumpActionAuditVerdict.QUEUED));
        for (int i = 0; i < suppressed; i++)
            decisions.add(decision(AdzumpActionAuditVerdict.SUPPRESSED));
        return new ApplyResult(CID, decisions, applied > 0 ? ULong.valueOf(42) : null);
    }

    private static ApplyDecision decision(AdzumpActionAuditVerdict verdict) {
        return new ApplyDecision(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD, verdict,
                verdict.getLiteral(), ULong.valueOf(1));
    }

    private static AutonomyConfig autonomyWithCadence(String cadence) {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("schedule").put("optimizationCadence", cadence);
        return new AutonomyConfig().setClientCode(CLIENT).setBody(body);
    }

    private static Thread daemon(Runnable body, AtomicReference<Throwable> error) {
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.setDaemon(true);
        return thread;
    }
}
