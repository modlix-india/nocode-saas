package com.modlix.saas.adzump.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.lang.reflect.Method;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

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
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ActionApplier;
import com.modlix.saas.adzump.service.apply.ApplyPlan;
import com.modlix.saas.adzump.service.apply.ApplyResult;
import com.modlix.saas.adzump.service.apply.AutonomyRouter;
import com.modlix.saas.adzump.service.creative.CreativeScore;
import com.modlix.saas.adzump.service.creative.CreativeScoringService;
import com.modlix.saas.adzump.service.experiment.ExperimentService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Gap-3 tests for {@link ExperimentController}: the readout + decide HTTP endpoints that make the
 * experiment lifecycle progress over the wire. The controller is a thin pass-through, so these run the
 * <b>real</b> {@link ExperimentService} (with the DAO / creative-scoring / apply / autonomy / security
 * collaborators mocked and the real {@link AutonomyRouter}), driving it through the controller so the
 * end-to-end HTTP → service behavior is asserted offline:
 * <ul>
 * <li>{@code GET /{id}/readout} returns the maturity-aware {@link ExperimentReadout} (per-variant outcomes +
 *     winner + pValue + significant) for a tenant-scoped experiment;</li>
 * <li>{@code POST /{id}/decide} delegates to {@link ExperimentService#decide}, whose promote/retire is routed
 *     as a single plan <b>through the J13 spine ({@link ActionApplier})</b> — a {@code SHIFT_BUDGET} +
 *     {@code PAUSE_ENTITY}, never {@code CampaignService}/SPI directly (the service holds no such reference) —
 *     and whose service method is {@code @PreAuthorize(EDIT)} while the controller carries none;</li>
 * <li>a cross-client experiment id (acting-as an unmanaged foreign client) is denied before any apply.</li>
 * </ul>
 */
class ExperimentControllerTest {

    private static final String OWN = "CLI0";
    private static final String FOREIGN = "CLI_OTHER";
    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";
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

    private ExperimentController controller;

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

        ExperimentService service = new ExperimentService(this.experimentDao, this.campaignPlanService,
                this.creativeScoringService, this.actionApplier, new AutonomyRouter(),
                this.autonomyConfigService, this.security, MSG);
        this.controller = new ExperimentController(service);

        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(plan());
        when(this.experimentDao.update(any(Experiment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // GET /{id}/readout — returns the maturity-aware readout for a tenant-scoped experiment
    // =====================================================================================

    @Test
    void readout_endpoint_returnsReadout_forTenantScopedExperiment() {
        Experiment exp = running();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_win"), any()))
                .thenReturn(score("cr_win", 60.0d, 400L, SignalMaturity.MATURE));
        when(this.creativeScoringService.getScore(eq(PLAN_ID), eq("cr_lose"), any()))
                .thenReturn(score("cr_lose", 40.0d, 400L, SignalMaturity.MATURE));

        ResponseEntity<ExperimentReadout> response = this.controller.readout(EXP_ID, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExperimentReadout readout = response.getBody();
        assertNotNull(readout, "the endpoint returns the ExperimentReadout, not the wrapping Experiment");
        assertTrue(readout.isSignificant());
        assertEquals("cr_win", readout.getWinner());
        assertEquals(2, readout.getPerVariant().size());
        assertNotNull(readout.getPValue());
        // maturity-aware verdict is surfaced per-arm inside the readout
        assertTrue(readout.getPerVariant().stream().allMatch(VariantOutcome::isJudgeable));
    }

    // =====================================================================================
    // POST /{id}/decide — delegates to the service; promote/retire routes through J13
    // =====================================================================================

    @Test
    void decide_endpoint_delegatesToService_routesPromoteRetireThroughJ13ActionApplier() {
        Experiment exp = significant();
        when(this.experimentDao.readById(EXP_ID)).thenReturn(exp);
        when(this.actionApplier.apply(eq(PLAN_ID), any(), any(), any()))
                .thenReturn(new ApplyResult(PLAN_ID, List.of(), null));

        ResponseEntity<Experiment> response = this.controller.decide(EXP_ID, null);

        // PROMOTE + RETIRE are routed as ONE plan THROUGH the J13 spine (ActionApplier) — the service holds no
        // CampaignService / platform-SPI reference, so a live change can only flow through this apply spine.
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

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(AdzumpExperimentStatus.APPLIED, response.getBody().getStatus());
    }

    @Test
    void decide_serviceMethod_isEditGated_whileTheThinControllerCarriesNone() throws NoSuchMethodException {
        // The mutating verb's authority lives on the service (@PreAuthorize EDIT), never on the controller.
        Method serviceDecide = ExperimentService.class.getMethod("decide", ULong.class, String.class);
        PreAuthorize edit = serviceDecide.getAnnotation(PreAuthorize.class);
        assertNotNull(edit, "ExperimentService.decide must be @PreAuthorize(EDIT)");
        assertEquals(EDIT, edit.value());

        Method controllerDecide = ExperimentController.class.getMethod("decide", ULong.class, String.class);
        assertNull(controllerDecide.getAnnotation(PreAuthorize.class),
                "the thin controller carries no @PreAuthorize — authz is on the service");
    }

    // =====================================================================================
    // Tenant scope — a cross-client experiment id is denied before any apply
    // =====================================================================================

    @Test
    void decide_endpoint_crossClientExperiment_isDenied_noApply() {
        // Caller's own client is OWN; acting-as an unmanaged foreign client is refused by the tenant rule.
        when(this.ca.getUser()).thenReturn(mock(ContextUser.class));
        when(this.security.isUserClientManageClient(any(), any(), any(), any())).thenReturn(false);

        assertThrows(GenericException.class, () -> this.controller.decide(EXP_ID, FOREIGN));

        verify(this.actionApplier, never()).apply(any(), any(), any(), any());
        verify(this.creativeScoringService, never()).recordCreativeAttributes(any(), any(), any());
        // denied at the tenant gate — never even loads the experiment row
        verify(this.experimentDao, never()).readById(any());
    }

    @Test
    void readout_endpoint_crossClientExperiment_isDenied() {
        when(this.ca.getUser()).thenReturn(mock(ContextUser.class));
        when(this.security.isUserClientManageClient(any(), any(), any(), any())).thenReturn(false);

        assertThrows(GenericException.class, () -> this.controller.readout(EXP_ID, FOREIGN));

        verify(this.experimentDao, never()).readById(any());
    }

    // =====================================================================================
    // Fixtures (mirror ExperimentServiceTest)
    // =====================================================================================

    private static CreativeScore score(String creativeId, double score, long volume, SignalMaturity maturity) {
        return new CreativeScore(creativeId, score, volume, 0.1d, maturity, maturity == SignalMaturity.MATURE, 3);
    }

    /** A RUNNING two-arm experiment isolating the {@code angle} axis (cr_win=possession, cr_lose=investor). */
    private static Experiment running() {
        Experiment exp = new Experiment()
                .setClientCode(OWN)
                .setCampaignPlanId(PLAN_ID)
                .setHypothesis("possession beats investor")
                .setMetric("blendedScore@creative")
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
