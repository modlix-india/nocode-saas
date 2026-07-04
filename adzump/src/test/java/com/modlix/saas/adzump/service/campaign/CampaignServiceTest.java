package com.modlix.saas.adzump.service.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.platform.AdPlatformRegistry;
import com.modlix.saas.adzump.platform.NoopPlatform;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PlanValidationService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * J8 campaign-lifecycle unit tests, run entirely offline via the J2b {@link NoopPlatform} double
 * registered under META + GOOGLE in a hand-built {@link AdPlatformRegistry} — no network, no SDK.
 * Uses a real {@link AdzumpMessageResourceService} (so guard/tenant failures raise real
 * {@link GenericException}s) and mocks the persistence / connection / security collaborators.
 *
 * <p>Covers the P1 exit for J8: launch fan-out happy path, forced-Meta partial failure (no rollback,
 * no throw), idempotent resume of the missing platform, illegal status transition rejected,
 * unstudied-product refusal, and tenant deny.
 */
class CampaignServiceTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final ULong PLAN_ID = ULong.valueOf(100);

    private CampaignPlanService planService;
    private PlanValidationService validationService;
    private ConnectionService connections;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;

    @BeforeEach
    void setUp() {
        this.planService = mock(CampaignPlanService.class);
        this.validationService = mock(PlanValidationService.class);
        this.connections = mock(ConnectionService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn("CLI0");

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // Tests
    // =====================================================================================

    @Test
    void launchFanOutHappyPath_writesLinksAndLivePaused() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        CampaignPlan plan = plan(AdzumpCampaignPlanStatus.DRAFT, "real_estate", null);
        when(this.planService.read(PLAN_ID)).thenReturn(plan);
        when(this.validationService.validate(eq(PLAN_ID), any())).thenReturn(valid());
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_meta"));
        when(this.connections.resolve(Platform.GOOGLE)).thenReturn(credential("act_google"));
        when(this.planService.writeStatusAndBody(any(), any(), any()))
                .thenAnswer(inv -> planWith(inv.getArgument(1)));

        CampaignPlan result = service.launch(PLAN_ID, null);

        assertEquals(AdzumpCampaignPlanStatus.LIVE_PAUSED, result.getStatus());

        ArgumentCaptor<AdzumpCampaignPlanStatus> statusCap = ArgumentCaptor.forClass(AdzumpCampaignPlanStatus.class);
        ArgumentCaptor<JsonNode> bodyCap = ArgumentCaptor.forClass(JsonNode.class);
        verify(this.planService).writeStatusAndBody(eq(PLAN_ID), statusCap.capture(), bodyCap.capture());

        assertEquals(AdzumpCampaignPlanStatus.LIVE_PAUSED, statusCap.getValue());
        JsonNode body = bodyCap.getValue();
        assertEquals("noop-google-campaign-1", body.at("/links/google/campaignId").asText());
        assertEquals("noop-meta-campaign-1", body.at("/links/meta/campaignId").asText());

        assertEquals(1, meta.getLaunchedCampaigns().size());
        assertEquals(1, google.getLaunchedCampaigns().size());
    }

    @Test
    void partialFailure_metaFails_yieldsPartiallyLaunched_googleIntact_noThrow() {

        NoopPlatform meta = new NoopPlatform(Platform.META, true); // forced fail-mode
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        CampaignPlan plan = plan(AdzumpCampaignPlanStatus.DRAFT, "real_estate", null);
        when(this.planService.read(PLAN_ID)).thenReturn(plan);
        when(this.validationService.validate(eq(PLAN_ID), any())).thenReturn(valid());
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_meta"));
        when(this.connections.resolve(Platform.GOOGLE)).thenReturn(credential("act_google"));
        when(this.planService.writeStatusAndBody(any(), any(), any()))
                .thenAnswer(inv -> planWith(inv.getArgument(1)));

        // No exception: the Meta failure is isolated.
        CampaignPlan result = service.launch(PLAN_ID, null);
        assertEquals(AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED, result.getStatus());

        ArgumentCaptor<AdzumpCampaignPlanStatus> statusCap = ArgumentCaptor.forClass(AdzumpCampaignPlanStatus.class);
        ArgumentCaptor<JsonNode> bodyCap = ArgumentCaptor.forClass(JsonNode.class);
        verify(this.planService).writeStatusAndBody(eq(PLAN_ID), statusCap.capture(), bodyCap.capture());

        assertEquals(AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED, statusCap.getValue());
        JsonNode body = bodyCap.getValue();
        // Google's ids are persisted...
        assertEquals("noop-google-campaign-1", body.at("/links/google/campaignId").asText());
        // ...Meta's are NOT (nothing to write, and no rollback of Google).
        assertTrue(body.at("/links/meta").isMissingNode());
        assertEquals(1, google.getLaunchedCampaigns().size());
    }

    @Test
    void idempotentRelaunch_completesOnlyTheMissingPlatform() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        // Google already launched (from a prior partial launch); Meta still missing.
        Links existing = new Links().setGoogle(
                new Links.GoogleLinks().setAdAccountId("ga").setCampaignId("g-existing"));
        CampaignPlan plan = plan(AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED, "real_estate", existing);

        when(this.planService.read(PLAN_ID)).thenReturn(plan);
        when(this.validationService.validate(eq(PLAN_ID), any())).thenReturn(valid());
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_meta"));
        when(this.planService.writeStatusAndBody(any(), any(), any()))
                .thenAnswer(inv -> planWith(inv.getArgument(1)));

        CampaignPlan result = service.launch(PLAN_ID, null);

        // Google was NOT re-created; only Meta was launched this time.
        assertTrue(google.getLaunchedCampaigns().isEmpty());
        assertEquals(1, meta.getLaunchedCampaigns().size());
        verify(this.connections, never()).resolve(Platform.GOOGLE);
        verify(this.connections).resolve(Platform.META);

        // Both platforms now ok -> LIVE_PAUSED; only Meta's ids are in the patch (Google preserved by merge).
        assertEquals(AdzumpCampaignPlanStatus.LIVE_PAUSED, result.getStatus());
        ArgumentCaptor<JsonNode> bodyCap = ArgumentCaptor.forClass(JsonNode.class);
        verify(this.planService).writeStatusAndBody(eq(PLAN_ID), eq(AdzumpCampaignPlanStatus.LIVE_PAUSED),
                bodyCap.capture());
        assertEquals("noop-meta-campaign-1", bodyCap.getValue().at("/links/meta/campaignId").asText());
    }

    @Test
    void illegalStatusTransition_rejected() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        // A DRAFT plan cannot be activated (DRAFT -> ACTIVE is not a legal edge).
        CampaignPlan plan = plan(AdzumpCampaignPlanStatus.DRAFT, "real_estate", null);
        when(this.planService.read(PLAN_ID)).thenReturn(plan);

        assertThrows(GenericException.class, () -> service.setStatus(PLAN_ID, RunState.ACTIVE, null));

        verify(this.planService, never()).writeStatusAndBody(any(), any(), any());
        assertTrue(meta.getStatusCalls().isEmpty());
        assertTrue(google.getStatusCalls().isEmpty());
    }

    @Test
    void unstudiedProduct_refused() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        // No vertical => never studied (P1 proxy for "productId has a studied profile", CONTRACT §6.2).
        CampaignPlan plan = plan(AdzumpCampaignPlanStatus.DRAFT, null, null);
        when(this.planService.read(PLAN_ID)).thenReturn(plan);
        when(this.validationService.validate(eq(PLAN_ID), any())).thenReturn(valid()); // J6 passes; the guard refuses

        assertThrows(GenericException.class, () -> service.launch(PLAN_ID, null));

        verify(this.planService, never()).writeStatusAndBody(any(), any(), any());
        assertTrue(meta.getLaunchedCampaigns().isEmpty());
        assertTrue(google.getLaunchedCampaigns().isEmpty());
    }

    @Test
    void tenantDeny_foreignClientTheCallerCannotManage_forbidden() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        assertThrows(GenericException.class, () -> service.launch(PLAN_ID, "OTHER"));

        // Denied at effective-client resolution, before the plan is even read.
        verify(this.planService, never()).read(any());
        verify(this.planService, never()).writeStatusAndBody(any(), any(), any());
    }

    @Test
    void liveLaunchDisabled_refusesBeforeAnyReadOrPlatformCall() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        CampaignService service = service(meta, google);
        ReflectionTestUtils.setField(service, "liveEnabled", false); // money-safety kill-switch engaged

        // The switch fires at the very top of launch(): before read / J6 validate / J7 compile / SPI.
        GenericException ex = assertThrows(GenericException.class, () -> service.launch(PLAN_ID, null));
        assertTrue(ex.getMessage().contains("Live launch is disabled"));

        verify(this.planService, never()).read(any());
        verify(this.planService, never()).writeStatusAndBody(any(), any(), any());
        assertTrue(meta.getLaunchedCampaigns().isEmpty());
        assertTrue(google.getLaunchedCampaigns().isEmpty());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private CampaignService service(NoopPlatform meta, NoopPlatform google) {
        AdPlatformRegistry registry = new AdPlatformRegistry(List.of(meta, google), MSG);
        CampaignService service = new CampaignService(this.planService, this.validationService, registry,
                this.connections, MSG, this.security);
        // Money-safety kill-switch defaults false (no @Value resolution outside Spring); the launch/
        // fan-out tests exercise a real launch, so enable it. The disabled-path test flips it back.
        ReflectionTestUtils.setField(service, "liveEnabled", true);
        return service;
    }

    private static CampaignPlan plan(AdzumpCampaignPlanStatus status, String vertical, Links links) {

        Map<Platform, CampaignType> types = new EnumMap<>(Platform.class);
        types.put(Platform.META, CampaignType.LEADS);
        types.put(Platform.GOOGLE, CampaignType.SEARCH);

        CampaignPlanBody body = new CampaignPlanBody()
                .setBudget(new BudgetPlan().setCurrency("USD").setDailyBudget(new Money(new BigDecimal("50"), "USD")))
                .setLinks(links);

        // setId lives on AbstractDTO (chain accessor returns the base type), so it is set separately
        // rather than mid-chain with the CampaignPlan-declared setters.
        CampaignPlan plan = new CampaignPlan()
                .setClientCode("CLI0")
                .setProductId("prd_1")
                .setVertical(vertical)
                .setName("Test Plan")
                .setStatus(status)
                .setCampaignTypes(types)
                .setBody(body);
        plan.setId(PLAN_ID);
        return plan;
    }

    private static CampaignPlan planWith(AdzumpCampaignPlanStatus status) {
        CampaignPlan plan = new CampaignPlan().setStatus(status);
        plan.setId(PLAN_ID);
        return plan;
    }

    private static ValidationResult valid() {
        return new ValidationResult().setValid(true).setIssues(List.of());
    }

    private static PlatformCredential credential(String accountId) {
        return new PlatformCredential()
                .setAccessToken("noop-token")
                .setAccountId(accountId)
                .setAttributes(Map.of());
    }
}
