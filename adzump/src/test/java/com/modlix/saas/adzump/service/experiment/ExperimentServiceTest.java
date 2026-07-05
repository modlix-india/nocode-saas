package com.modlix.saas.adzump.service.experiment;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.dao.ExperimentDao;
import com.modlix.saas.adzump.dto.Experiment;
import com.modlix.saas.adzump.dto.ExperimentReadout;
import com.modlix.saas.adzump.dto.ExperimentVariant;
import com.modlix.saas.adzump.dto.VariantOutcome;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpExperimentStatus;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ActionApplier;
import com.modlix.saas.adzump.service.apply.ApplyPlan;
import com.modlix.saas.adzump.service.apply.ApplyResult;
import com.modlix.saas.adzump.service.apply.AutonomyRouter;
import com.modlix.saas.adzump.service.creative.CreativeScore;
import com.modlix.saas.adzump.service.creative.CreativeScoringService;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Offline unit tests for {@link ExperimentService} — the J21 explore engine, run with no live platform / CRM
 * / scheduler. The DAO / creative-scoring / apply / autonomy collaborators are mocked while the <b>real</b>
 * {@link AutonomyRouter} routes, so the tests exercise the load-bearing behavior:
 * <ul>
 * <li>design rejects a confounded (multi-axis) variant set (attribute-isolation), accepts an isolated one;</li>
 * <li>the readout significance math on seeded arms (mature + separated =&gt; SIGNIFICANT winner);</li>
 * <li>the maturity gate blocks a fast-only "win" =&gt; INCONCLUSIVE (no false learning);</li>
 * <li>the mature-but-not-separated INCONCLUSIVE path, and the below-min-volume RUNNING wait;</li>
 * <li>decide PROMOTES + RETIRES <b>through {@link ActionApplier}</b> (a SHIFT_BUDGET + a PAUSE_ENTITY, never
 *     CampaignService/SPI directly) and writes the causal attribute result to J20;</li>
 * <li>start rotates the arms live through the same J13 spine.</li>
 * </ul>
 */
class ExperimentServiceTest {

    private static final String OWN = "CLI0";
    private static final ULong PLAN_ID = ULong.valueOf(100);
    private static final ULong EXP_ID = ULong.valueOf(777);
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private ExperimentDao experimentDao;
    private CampaignPlanService campaignPlanService;
    private CreativeScoringService creativeScoringService;
    private ActionApplier actionApplier;
    private AutonomyConfigService autonomyConfigService;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;

    private ExperimentService service;

    @BeforeEach
    void setUp() {
        this.experimentDao = mock(ExperimentDao.class);
        this.campaignPlanService = mock(CampaignPlanService.class);
        this.creativeScoringService = mock(CreativeScoringService.class);
        this.actionApplier = mock(ActionApplier.class);
        this.autonomyConfigService = mock(AutonomyConfigService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(OWN);
        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        this.service = new ExperimentService(this.experimentDao, this.campaignPlanService,
                this.creativeScoringService, this.actionApplier, new AutonomyRouter(),
                this.autonomyConfigService, this.security, MSG);

        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(plan());
        when(this.experimentDao.create(any())).thenAnswer(inv -> {
            Experiment e = inv.getArgument(0);
            e.setId(EXP_ID);
            return e;
        });
        when(this.experimentDao.update(any(Experiment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // Design — attribute isolation
    // =====================================================================================

    @Test
    void design_rejectsMultiAttributeVariantSet_asConfounded() {
        List<ExperimentVariant> variants = new ArrayList<>(List.of(
                variant("crA", Map.of("angle", "possession", "cta", "book")),
                variant("crB", Map.of("angle", "investor", "cta", "call"))));

        assertThrows(GenericException.class,
                () -> this.service.design(PLAN_ID, "h", variants, null, null, null, null));
        verify(this.experimentDao, never()).create(any());
    }

    @Test
    void design_rejectsNoContrastVariantSet() {
        List<ExperimentVariant> variants = new ArrayList<>(List.of(
                variant("crA", Map.of("angle", "possession")),
                variant("crB", Map.of("angle", "possession"))));

        assertThrows(GenericException.class,
                () -> this.service.design(PLAN_ID, "h", variants, null, null, null, null));
        verify(this.experimentDao, never()).create(any());
    }

    @Test
    void design_acceptsIsolatedSet_persistsDesigned_evenAllocation_defaults() {
        List<ExperimentVariant> variants = new ArrayList<>(List.of(
                variant("crA", Map.of("angle", "possession", "cta", "book")),
                variant("crB", Map.of("angle", "investor", "cta", "book"))));

        this.service.design(PLAN_ID, "possession beats investor", variants, null, null, null, null);

        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(this.experimentDao).create(cap.capture());
        Experiment e = cap.getValue();

        assertEquals(AdzumpExperimentStatus.DESIGNED, e.getStatus());
        assertEquals(OWN, e.getClientCode()); // pinned from the plan, never a body value
        assertEquals(ExperimentService.DEFAULT_METRIC, e.getMetric());
        assertEquals(ExperimentService.DEFAULT_MIN_VOLUME_PER_VARIANT, e.getMinVolumePerVariant());
        assertEquals(ExperimentService.DEFAULT_MAX_DURATION_DAYS, e.getMaxDurationDays());
        assertEquals(0.5d, e.getVariants().get(0).getAllocation(), 1e-9d);
        assertEquals(0.5d, e.getVariants().get(1).getAllocation(), 1e-9d);
    }

    // =====================================================================================
    // Readout — significance, maturity gate, inconclusive, running
    // =====================================================================================

    @Test
    void readout_matureAndSeparated_declaresSignificantWinner() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 60.0d, 400L, SignalMaturity.MATURE));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 40.0d, 400L, SignalMaturity.MATURE));

        this.service.readout(EXP_ID, null);

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.SIGNIFICANT, updated.getStatus());
        ExperimentReadout readout = updated.getReadout();
        assertTrue(readout.isSignificant());
        assertEquals("cr_win", readout.getWinner());
        assertTrue(readout.getPValue() < 1.0e-3d, "pValue=" + readout.getPValue());
        assertNotNull(updated.getEndedAt());
        assertEquals(2, readout.getPerVariant().size());
    }

    @Test
    void readout_fastOnlySignal_maturityGateBlocksWinner_inconclusive() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        // Scores are strongly separated, volume is past the cap — but the slow CRM signal is NOT mature.
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 60.0d, 400L, SignalMaturity.FAST_ONLY));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 40.0d, 400L, SignalMaturity.FAST_ONLY));

        this.service.readout(EXP_ID, null);

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.INCONCLUSIVE, updated.getStatus());
        assertFalse(updated.getReadout().isSignificant());
        assertNull(updated.getReadout().getWinner(), "no winner may be declared on immature signal");
        assertTrue(updated.getReadout().getPValue() < 1.0e-3d); // separated statistically, but not trusted
    }

    @Test
    void readout_matureButNotSeparated_isInconclusive() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 52.0d, 350L, SignalMaturity.MATURE));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 48.0d, 350L, SignalMaturity.MATURE));

        this.service.readout(EXP_ID, null);

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.INCONCLUSIVE, updated.getStatus());
        assertFalse(updated.getReadout().isSignificant());
        assertNull(updated.getReadout().getWinner());
        assertTrue(updated.getReadout().getPValue() > ExperimentStatistics.ALPHA);
    }

    @Test
    void readout_belowMinVolume_staysRunning_neverDeclaresEarly() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        // Wide separation + mature, but each arm is far below the 300 min volume: not yet judgeable as a win.
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 70.0d, 40L, SignalMaturity.MATURE));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 30.0d, 40L, SignalMaturity.MATURE));

        this.service.readout(EXP_ID, null);

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.RUNNING, updated.getStatus());
        assertFalse(updated.getReadout().isSignificant());
        assertNull(updated.getEndedAt());
    }

    @Test
    void stop_forcesTerminalReadout_neverLingersRunning() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 55.0d, 40L, SignalMaturity.MATURE));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 45.0d, 40L, SignalMaturity.MATURE));

        this.service.stop(EXP_ID, null);

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.INCONCLUSIVE, updated.getStatus());
        assertNotNull(updated.getEndedAt());
    }

    // =====================================================================================
    // Decide — promote/retire through J13 + causal learning to J20
    // =====================================================================================

    @Test
    void decide_promotesWinnerAndRetiresLoser_throughActionApplier_writesCausalResultToJ20() {
        Experiment exp = significant();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.actionApplier.apply(eq(PLAN_ID), any(), any(), any()))
                .thenReturn(new ApplyResult(PLAN_ID, List.of(), null));

        this.service.decide(EXP_ID, null);

        // PROMOTE + RETIRE are routed as a plan THROUGH the one J13 spine (not CampaignService/SPI directly).
        ArgumentCaptor<ApplyPlan> planCap = ArgumentCaptor.forClass(ApplyPlan.class);
        verify(this.actionApplier).apply(eq(PLAN_ID), planCap.capture(),
                eq(AdzumpActionAuditTriggeredBy.SCHEDULER), any());
        Set<AdzumpActionAuditActionType> types = planCap.getValue().routed().stream()
                .map(r -> r.action().type()).collect(Collectors.toSet());
        assertTrue(types.contains(AdzumpActionAuditActionType.SHIFT_BUDGET), "promote = SHIFT_BUDGET");
        assertTrue(types.contains(AdzumpActionAuditActionType.PAUSE_ENTITY), "retire = PAUSE_ENTITY");

        // The winning arm's attributes are recorded to J20 (the causal reinforcement of the map).
        ArgumentCaptor<Creative> creativeCap = ArgumentCaptor.forClass(Creative.class);
        verify(this.creativeScoringService).recordCreativeAttributes(eq("cr_win"), creativeCap.capture(), any());
        assertEquals(Map.of("angle", "possession"), creativeCap.getValue().getAttributes());

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.APPLIED, updated.getStatus());
    }

    @Test
    void decide_onInconclusiveExperiment_isRejected_noApplyNoLearning() {
        Experiment exp = running();
        exp.setStatus(AdzumpExperimentStatus.INCONCLUSIVE);
        exp.setReadout(new ExperimentReadout().setSignificant(false));
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);

        assertThrows(GenericException.class, () -> this.service.decide(EXP_ID, null));

        verify(this.actionApplier, never()).apply(any(), any(), any(), any());
        verify(this.creativeScoringService, never()).recordCreativeAttributes(any(), any(), any());
    }

    // =====================================================================================
    // Start — rotate live through J13
    // =====================================================================================

    @Test
    void start_rotatesVariantsLive_throughActionApplier_movesToRunning() {
        Experiment exp = running();
        exp.setStatus(AdzumpExperimentStatus.DESIGNED);
        exp.setStartedAt(null);
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.actionApplier.apply(eq(PLAN_ID), any(), any(), any()))
                .thenReturn(new ApplyResult(PLAN_ID, List.of(), null));

        this.service.start(EXP_ID, null);

        ArgumentCaptor<ApplyPlan> planCap = ArgumentCaptor.forClass(ApplyPlan.class);
        verify(this.actionApplier).apply(eq(PLAN_ID), planCap.capture(),
                eq(AdzumpActionAuditTriggeredBy.USER), any());
        Set<AdzumpActionAuditActionType> types = planCap.getValue().routed().stream()
                .map(r -> r.action().type()).collect(Collectors.toSet());
        assertTrue(types.contains(AdzumpActionAuditActionType.ROTATE_CREATIVE));

        Experiment updated = capturedUpdate();
        assertEquals(AdzumpExperimentStatus.RUNNING, updated.getStatus());
        assertNotNull(updated.getStartedAt());
    }

    @Test
    void start_onAlreadyRunningExperiment_isRejected() {
        Experiment exp = running(); // status RUNNING
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);

        assertThrows(GenericException.class, () -> this.service.start(EXP_ID, null));
        verify(this.actionApplier, never()).apply(any(), any(), any(), any());
    }

    // =====================================================================================
    // Fixtures
    // =====================================================================================

    private Experiment capturedUpdate() {
        ArgumentCaptor<Experiment> cap = ArgumentCaptor.forClass(Experiment.class);
        verify(this.experimentDao).update(cap.capture());
        return cap.getValue();
    }

    private static ExperimentVariant variant(String creativeId, Map<String, String> attributes) {
        return new ExperimentVariant().setCreativeId(creativeId).setAttributes(attributes);
    }

    private static CreativeScore score(String creativeId, double score, long volume, SignalMaturity maturity) {
        return new CreativeScore(creativeId, score, volume, 0.1d, maturity, maturity == SignalMaturity.MATURE, 3);
    }

    /** A RUNNING two-arm experiment isolating the {@code angle} axis (cr_win=possession, cr_lose=investor). */
    private static Experiment running() {
        Experiment exp = new Experiment()
                .setClientCode(OWN)
                .setCampaignPlanId(PLAN_ID)
                .setHypothesis("possession beats investor")
                .setMetric(ExperimentService.DEFAULT_METRIC)
                .setMinVolumePerVariant(300)
                .setMaxDurationDays(14)
                .setStatus(AdzumpExperimentStatus.RUNNING)
                .setVariants(new ArrayList<>(List.of(
                        new ExperimentVariant().setCreativeId("cr_win")
                                .setAttributes(Map.of("angle", "possession")).setAllocation(0.5d),
                        new ExperimentVariant().setCreativeId("cr_lose")
                                .setAttributes(Map.of("angle", "investor")).setAllocation(0.5d))))
                .setStartedAt(LocalDateTime.now());
        exp.setId(EXP_ID);
        return exp;
    }

    /** A SIGNIFICANT experiment whose readout has already named cr_win the winner. */
    private static Experiment significant() {
        Experiment exp = running();
        exp.setStatus(AdzumpExperimentStatus.SIGNIFICANT);
        exp.setEndedAt(LocalDateTime.now());
        exp.setReadout(new ExperimentReadout()
                .setPerVariant(List.of(
                        new VariantOutcome().setCreativeId("cr_win").setScore(60.0d).setVolume(400L)
                                .setMaturity(SignalMaturity.MATURE).setJudgeable(true),
                        new VariantOutcome().setCreativeId("cr_lose").setScore(40.0d).setVolume(400L)
                                .setMaturity(SignalMaturity.MATURE).setJudgeable(true)))
                .setWinner("cr_win")
                .setPValue(1.0e-8d)
                .setSignificant(true)
                .setComputedAt(LocalDateTime.now()));
        return exp;
    }

    private static CampaignPlan plan() {
        CampaignPlanBody body = new CampaignPlanBody()
                .setBudget(new BudgetPlan().setCurrency("USD").setDailyBudget(new Money(new BigDecimal("100"), "USD")));
        CampaignPlan plan = new CampaignPlan().setClientCode(OWN).setVertical("real_estate");
        plan.setId(PLAN_ID);
        plan.setBody(body);
        return plan;
    }
}
