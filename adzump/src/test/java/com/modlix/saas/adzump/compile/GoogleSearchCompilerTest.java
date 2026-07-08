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
 * Golden-payload tests for the Google SEARCH compiler — pure, zero API calls. Asserts the RSA caps
 * (15 headlines / 4 descriptions), money in micros, the keyword / negative-keyword shape, the
 * landing-page finalUrl (the website-attribution path), and fail-fast on a missing finalUrl.
 */
class GoogleSearchCompilerTest {

    private final GoogleSearchCompiler compiler = new GoogleSearchCompiler();

    private CompiledCampaign compile() {
        return this.compiler.compile(CompileFixtures.reSearchAndMetaLeads(), CompileFixtures.googleConfig());
    }

    @Test
    void tagsTheCompiledTreeWithGoogleAndSearch() {
        CompiledCampaign compiled = compile();
        assertEquals(Platform.GOOGLE, compiled.platform());
        assertEquals(CampaignType.SEARCH, compiled.type());
        assertEquals("Whitefield Launch - Site Visits", compiled.name());
    }

    @Test
    void campaignBudgetIsInMicrosWithMaximizeConversions() {
        JsonNode campaign = compile().payload().get("campaign");

        assertEquals("SEARCH", campaign.get("advertisingChannelType").asText());
        assertEquals("PAUSED", campaign.get("status").asText());
        assertEquals(3_000_000_000L, campaign.get("campaignBudget").get("amountMicros").asLong()); // 3000 * 1e6
        assertEquals("STANDARD", campaign.get("campaignBudget").get("deliveryMethod").asText());
        assertTrue(campaign.has("maximizeConversions"));
        assertTrue(campaign.get("networkSettings").get("targetGoogleSearch").asBoolean());
        assertEquals("2026-07-01", campaign.get("startDate").asText());
        assertEquals("2026-08-01", campaign.get("endDate").asText());
    }

    @Test
    void keywordsAndNegativesCarryMatchTypes() {
        JsonNode adGroup = compile().payload().get("adGroups").get(0);
        assertEquals("SEARCH_STANDARD", adGroup.get("type").asText());

        JsonNode keywords = adGroup.get("keywords");
        assertEquals(2, keywords.size());
        assertEquals("2 bhk whitefield", keywords.get(0).get("text").asText());
        assertEquals("PHRASE", keywords.get(0).get("matchType").asText());

        JsonNode negatives = adGroup.get("negativeKeywords");
        assertEquals(2, negatives.size());
        assertEquals("rent", negatives.get(0).get("text").asText());
        assertEquals("BROAD", negatives.get(0).get("matchType").asText());
    }

    @Test
    void rsaIsCappedAtFifteenHeadlinesAndFourDescriptions() {
        JsonNode rsa = compile().payload()
                .get("adGroups").get(0).get("ads").get(0).get("responsiveSearchAd");

        // The fixture supplies 18 headlines / 6 descriptions; Google caps at 15 / 4.
        assertEquals(15, rsa.get("headlines").size());
        assertEquals(4, rsa.get("descriptions").size());
        assertEquals("Headline 1", rsa.get("headlines").get(0).get("text").asText());
        assertEquals("Headline 15", rsa.get("headlines").get(14).get("text").asText());
    }

    @Test
    void adPointsAtTheInstrumentedLandingPage() {
        JsonNode ad = compile().payload().get("adGroups").get(0).get("ads").get(0);
        assertEquals("PAUSED", ad.get("status").asText());
        assertEquals(CompileFixtures.LANDING_URL, ad.get("finalUrls").get(0).asText());
    }

    @Test
    void metaAdGroupIsNotCompiledIntoTheGoogleTree() {
        // The combined plan has a Meta ad group too; the Google compiler must ignore it.
        JsonNode adGroups = compile().payload().get("adGroups");
        assertEquals(1, adGroups.size());
        assertEquals("Whitefield - intent", adGroups.get(0).get("name").asText());
    }

    @Test
    void missingFinalUrlThrowsInsteadOfDefaulting() {
        CampaignPlan plan = CompileFixtures.reSearchAndMetaLeads();
        // Drop both the ad's finalUrl and the landing page → nothing to point the RSA at.
        plan.getBody().getAdGroups().stream()
                .filter(ag -> ag.getPlatform() == Platform.GOOGLE)
                .forEach(ag -> ag.getAds().getFirst().setFinalUrl(null));
        plan.getBody().setLandingPage(null);

        assertThrows(IllegalStateException.class,
                () -> this.compiler.compile(plan, CompileFixtures.googleConfig()));
    }
}
