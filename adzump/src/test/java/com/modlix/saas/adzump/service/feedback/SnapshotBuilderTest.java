package com.modlix.saas.adzump.service.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.CrmOutcomes;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.leadzump.OutcomeQuery;
import com.modlix.saas.adzump.model.leadzump.OutcomeRow;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.platform.AdPlatform;
import com.modlix.saas.adzump.platform.AdPlatformRegistry;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.MilestoneMappingService;
import com.modlix.saas.adzump.service.PerformancePolicyService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.adzump.service.leadzump.LeadzumpClient;

/**
 * Offline unit tests for the J10 join (the differentiator), run entirely against mocks — no network,
 * no SDK. The SPI {@code insights} and the J11 {@code getOutcomes} are mocked (as the J8 tests mock
 * their collaborators); the real {@link PolicyScorer} is used. Covers the platform-only &rarr;
 * {@code FAST_ONLY} left-join, the MilestoneMapping fold, and window/timezone alignment across both
 * reads.
 */
class SnapshotBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final ULong PLAN_ID = ULong.valueOf(100);
    private static final String INR = "INR";
    private static final String CAMPAIGN_ID = "cmp-1";

    private CampaignPlanService campaignPlanService;
    private PerformancePolicyService performancePolicyService;
    private MilestoneMappingService milestoneMappingService;
    private ConnectionService connectionService;
    private LeadzumpClient leadzumpClient;
    private AdPlatform adPlatform;
    private SnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        this.campaignPlanService = mock(CampaignPlanService.class);
        this.performancePolicyService = mock(PerformancePolicyService.class);
        this.milestoneMappingService = mock(MilestoneMappingService.class);
        this.connectionService = mock(ConnectionService.class);
        this.leadzumpClient = mock(LeadzumpClient.class);

        this.adPlatform = mock(AdPlatform.class);
        when(this.adPlatform.code()).thenReturn(Platform.META);
        AdPlatformRegistry registry = new AdPlatformRegistry(List.of(this.adPlatform), MSG);

        this.builder = new SnapshotBuilder(this.campaignPlanService, this.performancePolicyService,
                this.milestoneMappingService, this.connectionService, registry, this.leadzumpClient,
                new PolicyScorer());

        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(plan());
        when(this.connectionService.resolve(Platform.META)).thenReturn(new PlatformCredential()
                .setAccessToken("tok").setAccountId("act_1").setAttributes(Map.of()));
        // Default: no effective policy / mapping (identity fold, scores drop to 0). Tests override.
        when(this.performancePolicyService.getEffective(eq(PLAN_ID), any())).thenReturn(null);
        when(this.milestoneMappingService.getEffective(eq(PLAN_ID), any())).thenReturn(null);
    }

    private static CampaignPlan plan() {
        Links links = new Links().setMeta(new Links.MetaLinks().setAdAccountId("act_1").setCampaignId(CAMPAIGN_ID));
        CampaignPlan plan = new CampaignPlan()
                .setClientCode("CLI0")
                .setProductId("prd_1")
                .setProductTemplateId("tmpl-re")
                .setVertical("real_estate")
                .setName("Plan")
                .setCampaignTypes(Map.of(Platform.META, CampaignType.LEADS))
                .setBody(new CampaignPlanBody().setLinks(links));
        plan.setId(PLAN_ID);
        return plan;
    }

    private static SnapshotWindow window() {
        return new SnapshotWindow().setFrom(LocalDate.of(2026, 6, 1)).setTo(LocalDate.of(2026, 6, 30))
                .setTimezone("Asia/Calcutta");
    }

    /** Only the CAMPAIGN grain yields a platform row; ADSET/AD return empty. */
    private void insightsAtCampaignGrainOnly(PlatformInsight campaignRow) {
        when(this.adPlatform.insights(any(), any())).thenAnswer(inv -> {
            InsightQuery q = inv.getArgument(1);
            return q.grain() == Grain.CAMPAIGN ? List.of(campaignRow) : List.of();
        });
    }

    private static PlatformInsight campaignInsight() {
        return new PlatformInsight(Grain.CAMPAIGN, new PlatformRef("campaign", CAMPAIGN_ID),
                2000L, 100L, new Money(new BigDecimal("5000"), INR), 8L);
    }

    // =====================================================================================

    @Test
    void platformRowWithNoCrm_leftJoinsToFastOnly_andDerivesCtrCpc() {

        insightsAtCampaignGrainOnly(campaignInsight());
        // CRM has no row for this ad-grain id -> left join leaves crm null.
        when(this.leadzumpClient.getOutcomes(any()))
                .thenReturn(new CrmOutcomes().setGrain(Grain.CAMPAIGN).setRows(List.of()));

        PerformanceSnapshot snapshot = this.builder.build(PLAN_ID, window(), null);

        assertEquals(1, snapshot.getGrainRows().size());
        SnapshotRow row = snapshot.getGrainRows().getFirst();

        assertEquals(Grain.CAMPAIGN, row.getGrain());
        assertEquals(CAMPAIGN_ID, row.getAdGrainId().getCampaignId());
        assertNull(row.getCrm(), "no CRM row joined -> crm must be null");
        assertEquals(SignalMaturity.FAST_ONLY, row.getSignalMaturity());

        // ctr = 100/2000 = 0.05 ; cpc = 5000/100 = 50.0000
        assertEquals(0.05d, row.getPlatform().getCtr(), 1e-9);
        assertEquals(0, new BigDecimal("50.0000").compareTo(row.getPlatform().getCpc().getAmount()));
        assertEquals(8L, row.getPlatform().getPlatformConversions());
    }

    @Test
    void crmRowJoinsById_andFoldsRawKeysOntoMilestones() {

        insightsAtCampaignGrainOnly(campaignInsight());

        // Raw leadzump keys: NEW + FOLLOW_UP roll up into "lead"; QUALIFIED into "qualified".
        OutcomeRow crmRow = new OutcomeRow()
                .setId(new AdGrainId().setCampaignId(CAMPAIGN_ID))
                .setCountByMilestone(Map.of("NEW", 10L, "FOLLOW_UP", 5L, "QUALIFIED", 8L))
                .setCostByMilestone(Map.of(
                        "NEW", new Money(new BigDecimal("200"), INR),
                        "FOLLOW_UP", new Money(new BigDecimal("300"), INR),
                        "QUALIFIED", new Money(new BigDecimal("800"), INR)))
                .setJunkRate(0.12);
        when(this.leadzumpClient.getOutcomes(any()))
                .thenReturn(new CrmOutcomes().setGrain(Grain.CAMPAIGN).setRows(List.of(crmRow)));

        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode milestones = body.putObject("milestones");
        ArrayNode lead = milestones.putArray("lead");
        lead.add("NEW");
        lead.add("FOLLOW_UP");
        milestones.putArray("qualified").add("QUALIFIED");
        when(this.milestoneMappingService.getEffective(eq(PLAN_ID), any()))
                .thenReturn(new MilestoneMapping().setBody(body));

        PerformanceSnapshot snapshot = this.builder.build(PLAN_ID, window(), null);

        SnapshotRow row = snapshot.getGrainRows().getFirst();
        CrmMetrics crm = row.getCrm();
        assertNotNull(crm);

        // counts sum: lead = 10 + 5 = 15 ; qualified = 8
        assertEquals(15L, crm.getCountByMilestone().get("lead"));
        assertEquals(8L, crm.getCountByMilestone().get("qualified"));

        // unit cost is the count-weighted average: lead = (200*10 + 300*5)/15 = 3500/15 = 233.3333
        assertEquals(0, new BigDecimal("233.3333").compareTo(crm.getCostByMilestone().get("lead").getAmount()));
        assertEquals(0, new BigDecimal("800.0000").compareTo(crm.getCostByMilestone().get("qualified").getAmount()));

        assertEquals(0.12d, crm.getJunkRate(), 1e-9);
    }

    @Test
    void bothReadsCarryTheSameWindowAndTimezone() {

        insightsAtCampaignGrainOnly(campaignInsight());
        when(this.leadzumpClient.getOutcomes(any()))
                .thenReturn(new CrmOutcomes().setGrain(Grain.CAMPAIGN).setRows(List.of()));

        this.builder.build(PLAN_ID, window(), null);

        ArgumentCaptor<InsightQuery> insightCap = ArgumentCaptor.forClass(InsightQuery.class);
        verify(this.adPlatform, org.mockito.Mockito.atLeastOnce()).insights(any(), insightCap.capture());
        InsightQuery iq = insightCap.getAllValues().stream()
                .filter(q -> q.grain() == Grain.CAMPAIGN).findFirst().orElseThrow();

        ArgumentCaptor<OutcomeQuery> outcomeCap = ArgumentCaptor.forClass(OutcomeQuery.class);
        verify(this.leadzumpClient).getOutcomes(outcomeCap.capture());
        OutcomeQuery oq = outcomeCap.getValue();

        // Same window + tz on both the FAST (platform) and SLOW (CRM) reads, so they are comparable.
        assertEquals(LocalDate.of(2026, 6, 1), iq.from());
        assertEquals(LocalDate.of(2026, 6, 30), iq.to());
        assertEquals("Asia/Calcutta", iq.timezone());

        assertEquals(iq.from(), oq.getFrom());
        assertEquals(iq.to(), oq.getTo());
        assertEquals(iq.timezone(), oq.getTimezone());
        assertEquals(Grain.CAMPAIGN, oq.getGrain());
        assertEquals("tmpl-re", oq.getProductTemplateId());
        assertEquals("CLI0", oq.getClientCode());
    }

    @Test
    void notLaunchedOnPlatform_yieldsEmptySnapshot_noReads() {

        CampaignPlan notLaunched = plan();
        notLaunched.getBody().setLinks(new Links().setMeta(new Links.MetaLinks().setAdAccountId("act_1")));
        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(notLaunched);

        PerformanceSnapshot snapshot = this.builder.build(PLAN_ID, window(), null);

        assertEquals(0, snapshot.getGrainRows().size());
        verify(this.adPlatform, org.mockito.Mockito.never()).insights(any(), any());
        verify(this.leadzumpClient, org.mockito.Mockito.never()).getOutcomes(any());
    }
}
