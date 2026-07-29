package com.modlix.saas.adzump.service.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
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
 * Offline tests for {@link OptimizationEngine#proposeAction}: a caller-proposed action is run through the
 * <b>same</b> {@link SignificanceGate} + {@link Objective} as an analyzer-born one. A real candidate on a
 * mature, high-volume grain comes back <b>gated</b> ({@code requiresApproval = true}) and <b>applies
 * nothing</b>; a thin-volume candidate is suppressed with {@code MIN_VOLUME}; a kill of a converter on
 * immature signal is suppressed with {@code IMMATURE_SIGNAL}; and a cross-client target the caller cannot
 * manage is denied before any read. The gate/objective are real; the J10 + config services are mocked.
 */
class ProposeActionTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private FeedbackService feedback;
    private CampaignPlanService planService;
    private PerformancePolicyService policyService;
    private AutonomyConfigService autonomyService;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;
    private OptimizationEngine engine;

    @BeforeEach
    void setUp() {
        this.feedback = mock(FeedbackService.class);
        this.planService = mock(CampaignPlanService.class);
        this.policyService = mock(PerformancePolicyService.class);
        this.autonomyService = mock(AutonomyConfigService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn("CLI0");

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        this.engine = new OptimizationEngine(java.util.List.of(), new SignificanceGate(),
                new Objective(new PolicyScorer()), this.feedback, this.planService, this.policyService,
                this.autonomyService, this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    private void stubReads(com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot snap) {
        when(this.feedback.readLatest(OptimizeFixtures.PLAN_ID, null)).thenReturn(snap);
        when(this.planService.read(OptimizeFixtures.PLAN_ID)).thenReturn(OptimizeFixtures.googlePlan());
        when(this.policyService.getEffective(OptimizeFixtures.PLAN_ID, null)).thenReturn(OptimizeFixtures.policy());
        when(this.autonomyService.getEffective(OptimizeFixtures.PLAN_ID, null)).thenReturn(null);
    }

    // =====================================================================================

    @Test
    void realCandidate_onMatureHighVolumeGrain_returnsGated_requiresApproval_appliesNothing() throws Exception {

        stubReads(OptimizeFixtures.underperformer());

        JsonNode change = OptimizeFixtures.MAPPER.readTree("{\"direction\":\"RAISE\","
                + "\"currentTargetCpa\":{\"amount\":800,\"currency\":\"INR\"},"
                + "\"proposedTargetCpa\":{\"amount\":700,\"currency\":\"INR\"},\"strategyHint\":\"target_cpa\"}");
        ProposedAction proposed = new ProposedAction(AdzumpActionAuditActionType.ADJUST_BID,
                OptimizeFixtures.adSetGrain("C1", "AS_WIN"), change,
                "raise the bid on the winning ad set", null, null, null);

        ActionSet set = this.engine.proposeAction(OptimizeFixtures.PLAN_ID, proposed, null);

        // Gated: exactly one action, requiresApproval, verdict PASSED, the typed change echoed back.
        assertEquals(1, set.actions().size());
        assertTrue(set.suppressed().isEmpty());
        Action action = set.actions().get(0);
        assertEquals(AdzumpActionAuditActionType.ADJUST_BID, action.type());
        assertTrue(action.requiresApproval());
        assertTrue(action.significance().passed());
        assertEquals(GateOutcome.PASSED, action.significance().outcome());
        assertInstanceOf(ActionChange.BidChange.class, action.change());
        assertEquals(9L, set.snapshotId().longValue());

        // Applies nothing: recommend-mode never builds/mutates (only the read was consulted).
        verify(this.feedback, never()).getSnapshot(any(), any(), any());
    }

    @Test
    void thinVolumeCandidate_isSuppressed_withMinVolumeReason() throws Exception {

        stubReads(OptimizeFixtures.noise());

        JsonNode change = OptimizeFixtures.MAPPER.readTree("{\"direction\":\"LOWER\","
                + "\"currentTargetCpa\":{\"amount\":800,\"currency\":\"INR\"},"
                + "\"proposedTargetCpa\":{\"amount\":1000,\"currency\":\"INR\"},\"strategyHint\":\"target_cpa\"}");
        ProposedAction proposed = new ProposedAction(AdzumpActionAuditActionType.ADJUST_BID,
                OptimizeFixtures.adSetGrain("C2", "AS_A"), change, "re-bid a thin ad set", null, null, null);

        ActionSet set = this.engine.proposeAction(OptimizeFixtures.PLAN_ID, proposed, null);

        // Suppressed, explainably: below the min-volume gate, and no objective movement is projected.
        assertTrue(set.actions().isEmpty());
        assertEquals(1, set.suppressed().size());
        SuppressedCandidate suppressed = set.suppressed().get(0);
        assertEquals(GateOutcome.MIN_VOLUME, suppressed.verdict().outcome());
        assertNotNull(suppressed.verdict().detail());
        assertFalse(suppressed.verdict().detail().isBlank());
        assertEquals(set.objectiveBefore(), set.objectiveProjectedAfter(), 1e-9);

        verify(this.feedback, never()).getSnapshot(any(), any(), any());
    }

    @Test
    void killOfConverterOnImmatureSignal_isSuppressed_withImmatureReason() throws Exception {

        stubReads(OptimizeFixtures.slowConverter());

        JsonNode change = OptimizeFixtures.MAPPER.readTree("{\"kill\":true,\"reason\":\"scoring low\"}");
        ProposedAction proposed = new ProposedAction(AdzumpActionAuditActionType.PAUSE_ENTITY,
                OptimizeFixtures.adGrain("C3", "AD_SLOW"), change, "pause the low-scoring ad", null, null, null);

        ActionSet set = this.engine.proposeAction(OptimizeFixtures.PLAN_ID, proposed, null);

        // A caller cannot talk a kill of a slow-converting winner past the maturity gate.
        assertTrue(set.actions().isEmpty());
        assertEquals(1, set.suppressed().size());
        assertEquals(GateOutcome.IMMATURE_SIGNAL, set.suppressed().get(0).verdict().outcome());

        verify(this.feedback, never()).getSnapshot(any(), any(), any());
    }

    @Test
    void crossClientDeny_forbiddenBeforeAnyRead() {

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        ProposedAction proposed = new ProposedAction(AdzumpActionAuditActionType.ADJUST_BID,
                new AdGrainId().setCampaignId("C1").setAdSetId("AS_WIN"), null, "x", null, null, null);

        assertThrows(GenericException.class,
                () -> this.engine.proposeAction(OptimizeFixtures.PLAN_ID, proposed, "OTHER"));

        // Denied at effective-client resolution, before any snapshot/plan read.
        verify(this.feedback, never()).readLatest(any(), any());
        verify(this.feedback, never()).readLatest(any(), any(), any());
        verify(this.planService, never()).read(any());
    }
}
