package com.modlix.saas.adzump.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.modlix.saas.adzump.compile.EffectiveConfig;
import com.modlix.saas.adzump.compile.MetaLeadsCompiler;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.CreativeFormat;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Copy;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.LeadForm;
import com.modlix.saas.adzump.model.LeadFormField;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.vertical.PolicyDefaults;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * Offline tests for the Meta create/mutate sequencer — {@link MetaGraphClient} is mocked, so no live
 * Graph call ever fires. Covers the load-bearing J3 facts:
 * <ul>
 * <li>a compiled RE LEADS plan issues the create calls in the exact order campaign → lead form →
 * ad set → creative → ad, with HOUSING declared and every object PAUSED;</li>
 * <li>a mid-sequence Graph failure rolls back everything created (LIFO) and returns a failed result
 * rather than throwing a half-built campaign upward;</li>
 * <li>a non-HOUSING real-estate lead-gen payload is rejected pre-flight, before any API call.</li>
 * </ul>
 */
class MetaLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private static final String ACCOUNT = "act_123456";
    private static final String PAGE_ID = "1112223334";
    private static final String LEAD_FORM_PLACEHOLDER = "lf-1";

    private static Token token() {
        return new Token("tok", ACCOUNT, null, Map.of());
    }

    // ---- fixtures ----------------------------------------------------------

    /** A real compiled RE Meta LEADS tree (HOUSING, ABO), via the real J7 compiler. */
    private static CompiledCampaign compiledReLeads() {
        return new MetaLeadsCompiler().compile(reLeadsPlan(), metaConfig());
    }

    private static CampaignPlan reLeadsPlan() {
        return new CampaignPlan()
                .setName("Whitefield Launch - Site Visits")
                .setProductId("prd_1")
                .setVertical("real_estate")
                .setCampaignTypes(Map.of(Platform.META, CampaignType.LEADS))
                .setBody(new CampaignPlanBody()
                        .setObjective(new Objective()
                                .setPlatformObjective(PlatformObjective.LEADS)
                                .setTargetCostPerOutcome(inr(28000)))
                        .setBudget(new BudgetPlan().setCurrency("INR").setDailyBudget(inr(3000)))
                        .setSchedule(new ScheduleConfig()
                                .setStartAt(LocalDateTime.of(2026, 7, 1, 0, 0))
                                .setEndAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                                .setTimezone("Asia/Kolkata"))
                        .setCompliance(new Compliance().setSpecialAdCategory(SpecialAdCategory.HOUSING))
                        .setAdGroups(List.of(new AdGroup()
                                .setId("ag_meta")
                                .setName("Whitefield - end users")
                                .setPlatform(Platform.META)
                                .setBudget(inr(3000))
                                .setAds(List.of(new Ad()
                                        .setId("ad_meta")
                                        .setName("Investment-ROI - interior render")
                                        .setCreativeId("cr_meta")
                                        .setLeadFormId(LEAD_FORM_PLACEHOLDER)
                                        .setCallToAction("BOOK_NOW")))))
                        .setCreatives(List.of(new Creative()
                                .setId("cr_meta")
                                .setFormat(CreativeFormat.IMAGE)
                                .setCopy(new Copy()
                                        .setPrimaryTexts(List.of("Assured-ROI homes in Whitefield"))
                                        .setHeadlines(List.of("2 & 3 BHK in Whitefield"))
                                        .setDescriptions(List.of("RERA-approved. Site visits open."))
                                        .setCta("BOOK_NOW"))))
                        .setLeadForm(new LeadForm()
                                .setId(LEAD_FORM_PLACEHOLDER)
                                .setPlatform(Platform.META)
                                .setPrivacyPolicyUrl("https://fincity.example/privacy")
                                .setFields(List.of(new LeadFormField().setKey("full_name").setType("FULL_NAME"))))
                        .setLinks(new Links().setMeta(new Links.MetaLinks()
                                .setAdAccountId(ACCOUNT)
                                .setPageId(PAGE_ID)
                                .setPixelId("2223334445"))));
    }

    private static EffectiveConfig metaConfig() {
        return new EffectiveConfig("INR", PolicyDefaults.BudgetMode.AD_SET, "MAXIMIZE_CONVERSIONS", null,
                "7d_click_1d_view", PlatformObjective.LEADS, SpecialAdCategory.HOUSING, "Asia/Kolkata");
    }

    private static Money inr(long amount) {
        return new Money(new BigDecimal(amount), "INR");
    }

    /** Fake Graph create reply: {@code {"id": <edge-derived id>}}. */
    private static JsonNode idFor(String edge) {
        String id;
        if (edge.endsWith("/campaigns"))
            id = "camp_1";
        else if (edge.endsWith("/leadgen_forms"))
            id = "form_1";
        else if (edge.endsWith("/adsets"))
            id = "adset_1";
        else if (edge.endsWith("/adcreatives"))
            id = "cr_1";
        else if (edge.endsWith("/ads"))
            id = "ad_1";
        else
            id = "x_1";
        return MAPPER.createObjectNode().put("id", id);
    }

    // ---- tests -------------------------------------------------------------

    @Test
    void launchIssuesCreatesInOrderWithHousingAndEverythingPaused() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.post(any(), anyString(), any())).thenAnswer(inv -> idFor(inv.getArgument(1)));

        MetaLifecycle lifecycle = new MetaLifecycle(graph, MSG);
        LaunchResult result = lifecycle.launchPaused(compiledReLeads(), token());

        // Success carries the created campaign id back on the Meta Links fragment for J8.
        assertTrue(result.ok());
        assertNotNull(result.links());
        assertNotNull(result.links().getMeta());
        assertEquals("camp_1", result.links().getMeta().getCampaignId());
        assertEquals(PAGE_ID, result.links().getMeta().getPageId());
        assertEquals(ACCOUNT, result.links().getMeta().getAdAccountId());

        ArgumentCaptor<String> edgeCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(graph, times(5)).post(any(), edgeCap.capture(), bodyCap.capture());

        // Sequence: campaign → lead form (on the page) → ad set → creative → ad. Order is load-bearing.
        assertEquals(List.of(
                ACCOUNT + "/campaigns",
                PAGE_ID + "/leadgen_forms",
                ACCOUNT + "/adsets",
                ACCOUNT + "/adcreatives",
                ACCOUNT + "/ads"), edgeCap.getAllValues());

        List<Map> bodies = bodyCap.getAllValues();

        // Campaign: HOUSING declared, PAUSED, before the API call (it is the first create).
        Map<?, ?> campaign = bodies.get(0);
        assertEquals("PAUSED", campaign.get("status"));
        assertEquals(List.of("HOUSING"), campaign.get("special_ad_categories"));

        // Ad set: PAUSED, wired to the created campaign, and the nested "ads" is stripped from the body.
        Map<?, ?> adSet = bodies.get(2);
        assertEquals("PAUSED", adSet.get("status"));
        assertEquals("camp_1", adSet.get("campaign_id"));
        assertFalse(adSet.containsKey("ads"));

        // Creative: the placeholder lead-form id is rebound to the id the form create returned.
        Map<?, ?> creative = bodies.get(3);
        Map<?, ?> oss = (Map<?, ?>) creative.get("object_story_spec");
        Map<?, ?> linkData = (Map<?, ?>) oss.get("link_data");
        Map<?, ?> cta = (Map<?, ?>) linkData.get("call_to_action");
        Map<?, ?> value = (Map<?, ?>) cta.get("value");
        assertEquals("form_1", value.get("lead_gen_form_id"));

        // Ad: PAUSED, referencing the created ad set + creative.
        Map<?, ?> ad = bodies.get(4);
        assertEquals("PAUSED", ad.get("status"));
        assertEquals("adset_1", ad.get("adset_id"));
        assertEquals(Map.of("creative_id", "cr_1"), ad.get("creative"));
    }

    @Test
    void midSequenceFailureRollsBackAndReturnsFailedWithoutThrowing() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        // Campaign / form / ad set succeed; the creative create blows up mid-sequence.
        when(graph.post(any(), anyString(), any())).thenAnswer(inv -> idFor(inv.getArgument(1)));
        when(graph.post(any(), argThat(e -> e != null && e.endsWith("/adcreatives")), any()))
                .thenThrow(new GenericException(org.springframework.http.HttpStatus.BAD_GATEWAY, "creative boom"));

        MetaLifecycle lifecycle = new MetaLifecycle(graph, MSG);

        // No throw of a half-built campaign — the failure is returned for J8 partial-failure handling.
        LaunchResult result = lifecycle.launchPaused(compiledReLeads(), token());
        assertFalse(result.ok());
        assertNotNull(result.error());
        assertEquals(Platform.META, result.platform());

        // Do-no-harm rollback: everything created before the failure is deleted, children before parents.
        ArgumentCaptor<String> del = ArgumentCaptor.forClass(String.class);
        verify(graph, times(3)).delete(any(), del.capture());
        assertEquals(List.of("adset_1", "form_1", "camp_1"), del.getAllValues());
    }

    @Test
    void nonHousingRealEstateLeadGenPayloadIsRejectedPreFlight() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        MetaLifecycle lifecycle = new MetaLifecycle(graph, MSG);

        // A LEADS payload that DECLARES it requires HOUSING but omits special_ad_categories: the exact
        // non-compliant shape J3 must catch before touching Meta (the legacy launched these live).
        ObjectNode payload = MAPPER.createObjectNode();
        ObjectNode campaign = payload.putObject("campaign");
        campaign.put("name", "Whitefield Launch");
        campaign.put("status", "PAUSED");
        payload.putObject("compliance").put("requiredSpecialAdCategory", "HOUSING");
        payload.putArray("adSets");

        CompiledCampaign nonCompliant = new CompiledCampaign(
                Platform.META, CampaignType.LEADS, "Whitefield Launch", null, payload);

        GenericException ex = assertThrows(GenericException.class,
                () -> lifecycle.launchPaused(nonCompliant, token()));
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("HOUSING"));

        // Rejected pre-flight: not a single Graph create was issued.
        verify(graph, never()).post(any(), anyString(), any());
    }
}
