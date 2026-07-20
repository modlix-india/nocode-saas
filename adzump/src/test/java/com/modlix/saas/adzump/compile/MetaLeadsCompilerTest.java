package com.modlix.saas.adzump.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.platform.CompiledCampaign;

/**
 * Golden-payload tests for the Meta LEADS compiler — pure, zero API calls. Asserts the load-bearing
 * facts: HOUSING special-ad-category present, HOUSING restricted-targeting shape (no age/gender),
 * money in account minor units, the on-ad lead-form wiring, and that a missing required id throws
 * rather than defaulting (the seam the legacy lacked).
 */
class MetaLeadsCompilerTest {

    private final MetaLeadsCompiler compiler = new MetaLeadsCompiler();

    private CompiledCampaign compile() {
        return this.compiler.compile(CompileFixtures.reSearchAndMetaLeads(), CompileFixtures.metaConfig());
    }

    @Test
    void tagsTheCompiledTreeWithMetaAndLeads() {
        CompiledCampaign compiled = compile();
        assertEquals(Platform.META, compiled.platform());
        assertEquals(CampaignType.LEADS, compiled.type());
        assertEquals("Whitefield Launch - Site Visits", compiled.name());
    }

    @Test
    void campaignCarriesHousingSpecialAdCategoryAndLeadsObjective() {
        JsonNode campaign = compile().payload().get("campaign");

        assertEquals("OUTCOME_LEADS", campaign.get("objective").asText());
        assertEquals("PAUSED", campaign.get("status").asText());

        JsonNode categories = campaign.get("special_ad_categories");
        assertTrue(categories.isArray());
        assertEquals(1, categories.size());
        assertEquals("HOUSING", categories.get(0).asText());
    }

    @Test
    void budgetIsOnTheAdSetInMinorUnitsUnderAbo() {
        JsonNode payload = compile().payload();

        // AD_SET (ABO): the campaign carries no budget; the ad set carries it, in minor units.
        assertFalse(payload.get("campaign").has("daily_budget"));

        JsonNode adSet = payload.get("adSets").get(0);
        assertEquals(300000L, adSet.get("daily_budget").asLong()); // 3000 INR * 100
    }

    @Test
    void adSetHasLeadGenDeliveryAndPromotedPage() {
        JsonNode adSet = compile().payload().get("adSets").get(0);

        assertEquals("ON_AD", adSet.get("destination_type").asText());
        assertEquals("LEAD_GENERATION", adSet.get("optimization_goal").asText());
        assertEquals("IMPRESSIONS", adSet.get("billing_event").asText());
        // MAXIMIZE_CONVERSIONS maps to Meta's automatic lowest-cost bidding.
        assertEquals("LOWEST_COST_WITHOUT_CAP", adSet.get("bid_strategy").asText());
        assertEquals(CompileFixtures.PAGE_ID, adSet.get("promoted_object").get("page_id").asText());
        assertEquals("2026-07-01T00:00:00+05:30", adSet.get("start_time").asText());
    }

    @Test
    void housingDropsAgeAndGenderNarrowingButKeepsGeoAndInterests() {
        JsonNode targeting = compile().payload().get("adSets").get(0).get("targeting");

        // Restricted-targeting shape under HOUSING: no age/gender narrowing.
        assertFalse(targeting.has("age_min"));
        assertFalse(targeting.has("age_max"));
        assertFalse(targeting.has("genders"));

        // Geo (radius) and interests survive.
        JsonNode custom = targeting.get("geo_locations").get("custom_locations").get(0);
        assertEquals(8, custom.get("radius").asInt());
        assertEquals("kilometer", custom.get("distance_unit").asText());
        assertEquals("6003629266583",
                targeting.get("flexible_spec").get(0).get("interests").get(0).get("id").asText());
    }

    @Test
    void attributionSpecParsedFromNeutralToken() {
        JsonNode spec = compile().payload().get("adSets").get(0).get("attribution_spec");
        assertEquals(2, spec.size());
        assertEquals("CLICK_THROUGH", spec.get(0).get("event_type").asText());
        assertEquals(7, spec.get(0).get("window_days").asInt());
        assertEquals("VIEW_THROUGH", spec.get(1).get("event_type").asText());
        assertEquals(1, spec.get(1).get("window_days").asInt());
    }

    @Test
    void adWiresTheCreativeToTheLeadFormViaCta() {
        JsonNode linkData = compile().payload()
                .get("adSets").get(0).get("ads").get(0)
                .get("creative").get("object_story_spec").get("link_data");

        assertEquals("Assured-ROI homes in Whitefield", linkData.get("message").asText());
        assertEquals("2 & 3 BHK in Whitefield", linkData.get("name").asText());
        assertEquals("http://fb.me/", linkData.get("link").asText());

        JsonNode cta = linkData.get("call_to_action");
        assertEquals("BOOK_NOW", cta.get("type").asText());
        assertEquals(CompileFixtures.LEAD_FORM_ID, cta.get("value").get("lead_gen_form_id").asText());
    }

    @Test
    void leadFormQuestionsExpandFromFields() {
        JsonNode leadForm = compile().payload().get("leadForms").get(0);
        assertEquals(CompileFixtures.LEAD_FORM_ID, leadForm.get("name").asText());

        JsonNode questions = leadForm.get("questions");
        assertEquals(4, questions.size());
        assertEquals("FULL_NAME", questions.get(0).get("type").asText());

        JsonNode budgetOptions = questions.get(3).get("options");
        assertEquals(3, budgetOptions.size());
        assertEquals("<80L", budgetOptions.get(0).get("value").asText());
        assertEquals("https://fincity.example/privacy", leadForm.get("privacy_policy").get("url").asText());
    }

    @Test
    void missingPageIdThrowsInsteadOfDefaulting() {
        // The legacy silently defaulted a missing page id; the fix is to fail fast.
        CampaignPlan plan = CompileFixtures.reSearchAndMetaLeads();
        plan.getBody().getLinks().getMeta().setPageId(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> this.compiler.compile(plan, CompileFixtures.metaConfig()));
        assertTrue(ex.getMessage().contains("pageId"));
    }
}
