package com.modlix.saas.adzump.platform.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.ads.googleads.v24.enums.AdGroupAdStatusEnum.AdGroupAdStatus;
import com.google.ads.googleads.v24.enums.AdGroupCriterionStatusEnum.AdGroupCriterionStatus;
import com.google.ads.googleads.v24.enums.AdGroupStatusEnum.AdGroupStatus;
import com.google.ads.googleads.v24.enums.AdGroupTypeEnum.AdGroupType;
import com.google.ads.googleads.v24.enums.AdvertisingChannelTypeEnum.AdvertisingChannelType;
import com.google.ads.googleads.v24.enums.CampaignStatusEnum.CampaignStatus;
import com.google.ads.googleads.v24.enums.KeywordMatchTypeEnum.KeywordMatchType;
import com.google.ads.googleads.v24.resources.AdGroup;
import com.google.ads.googleads.v24.resources.AdGroupAd;
import com.google.ads.googleads.v24.resources.AdGroupCriterion;
import com.google.ads.googleads.v24.resources.Campaign;
import com.google.ads.googleads.v24.resources.CampaignBudget;
import com.google.ads.googleads.v24.services.MutateCampaignResult;
import com.google.ads.googleads.v24.services.MutateGoogleAdsResponse;
import com.google.ads.googleads.v24.services.MutateOperation;
import com.google.ads.googleads.v24.services.MutateOperationResponse;
import com.modlix.saas.adzump.compile.EffectiveConfig;
import com.modlix.saas.adzump.compile.GoogleSearchCompiler;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.Bid;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.KeywordSpec;
import com.modlix.saas.adzump.model.LandingPageRef;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.Targeting;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.vertical.PolicyDefaults;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * Offline test of {@link GoogleLifecycle#launchPaused}: it compiles a real-estate Google SEARCH plan
 * with the J7 {@link GoogleSearchCompiler}, then launches it against a <b>mocked</b>
 * {@link GoogleAdsClientFacade} (no network / no gRPC). It captures the atomic
 * {@code MutateOperation} list and asserts the campaign / budget / ad-group / RSA / keyword /
 * negative-keyword operations are all built PAUSED and point the RSA at the landing-page finalUrl —
 * and that a missing MCC / customer context fails fast before the facade is ever touched.
 */
class GoogleLifecycleLaunchTest {

    private static final String LANDING_URL = "https://land.example/whitefield?utm_source=google";
    private static final String CUSTOMER_DASHED = "984-600-7422";
    private static final String CUSTOMER_DIGITS = "9846007422";
    private static final String MCC = "1234567890";

    private final AdzumpMessageResourceService msg = new AdzumpMessageResourceService();

    private static CompiledCampaign compiledSearchCampaign() {
        CampaignPlan plan = new CampaignPlan()
                .setName("Whitefield Launch")
                .setCampaignTypes(Map.of(Platform.GOOGLE, CampaignType.SEARCH))
                .setBody(new CampaignPlanBody()
                        .setObjective(new Objective().setPlatformObjective(PlatformObjective.LEADS))
                        .setBudget(new BudgetPlan().setCurrency("INR")
                                .setDailyBudget(new Money(new BigDecimal(3000), "INR")))
                        .setLandingPage(new LandingPageRef().setUrl(LANDING_URL))
                        .setAdGroups(List.of(new com.modlix.saas.adzump.model.AdGroup()
                                .setName("Whitefield - intent")
                                .setPlatform(Platform.GOOGLE)
                                .setBid(new Bid().setStrategy("MAXIMIZE_CONVERSIONS"))
                                .setTargeting(new Targeting()
                                        .setKeywords(List.of(
                                                new KeywordSpec().setText("2 bhk whitefield").setMatchType(MatchType.PHRASE),
                                                new KeywordSpec().setText("apartments in whitefield").setMatchType(MatchType.BROAD)))
                                        .setNegativeKeywords(List.of(
                                                new KeywordSpec().setText("rent").setMatchType(MatchType.BROAD),
                                                new KeywordSpec().setText("pg").setMatchType(MatchType.BROAD))))
                                .setAds(List.of(new Ad().setName("RSA - Whitefield").setFinalUrl(LANDING_URL)
                                        .setHeadlines(List.of("2 BHK in Whitefield", "RERA approved", "Book a site visit"))
                                        .setDescriptions(List.of("Assured-ROI homes.", "Site visits open now.")))))));

        EffectiveConfig cfg = new EffectiveConfig("INR", PolicyDefaults.BudgetMode.CAMPAIGN,
                "MAXIMIZE_CONVERSIONS", null, "30d_click", PlatformObjective.LEADS, null, null);

        return new GoogleSearchCompiler().compile(plan, cfg);
    }

    private static Token token(String customerId, String mcc) {
        return new Token("oauth-access-token", customerId, mcc, Map.of());
    }

    private static MutateGoogleAdsResponse cannedResponse() {
        return MutateGoogleAdsResponse.newBuilder()
                .addMutateOperationResponses(MutateOperationResponse.newBuilder()
                        .setCampaignResult(MutateCampaignResult.newBuilder()
                                .setResourceName("customers/" + CUSTOMER_DIGITS + "/campaigns/555000111")))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void launchPausedIssuesTheWholePausedTreeAsOneAtomicMutate() {

        GoogleAdsClientFacade facade = mock(GoogleAdsClientFacade.class);
        GoogleLifecycle lifecycle = new GoogleLifecycle(facade, this.msg);

        Token token = token(CUSTOMER_DASHED, MCC);
        ArgumentCaptor<List<MutateOperation>> captor = ArgumentCaptor.forClass(List.class);
        when(facade.mutate(eq(token), eq(CUSTOMER_DIGITS), captor.capture())).thenReturn(cannedResponse());

        LaunchResult result = lifecycle.launchPaused(compiledSearchCampaign(), token);

        // ids come back on the Links fragment for J8 to persist
        assertTrue(result.ok());
        assertEquals(Platform.GOOGLE, result.platform());
        assertEquals("555000111", result.links().getGoogle().getCampaignId());
        assertEquals(CUSTOMER_DIGITS, result.links().getGoogle().getAdAccountId());

        List<MutateOperation> ops = captor.getValue();

        CampaignBudget budget = ops.stream().filter(MutateOperation::hasCampaignBudgetOperation)
                .map(o -> o.getCampaignBudgetOperation().getCreate()).findFirst().orElseThrow();
        Campaign campaign = ops.stream().filter(MutateOperation::hasCampaignOperation)
                .map(o -> o.getCampaignOperation().getCreate()).findFirst().orElseThrow();
        AdGroup adGroup = ops.stream().filter(MutateOperation::hasAdGroupOperation)
                .map(o -> o.getAdGroupOperation().getCreate()).findFirst().orElseThrow();
        List<AdGroupCriterion> criteria = ops.stream().filter(MutateOperation::hasAdGroupCriterionOperation)
                .map(o -> o.getAdGroupCriterionOperation().getCreate()).toList();
        AdGroupAd adGroupAd = ops.stream().filter(MutateOperation::hasAdGroupAdOperation)
                .map(o -> o.getAdGroupAdOperation().getCreate()).findFirst().orElseThrow();

        // budget: micros from the compiled payload (3000 * 1e6), named, linked to the campaign by temp name
        assertEquals(3_000_000_000L, budget.getAmountMicros());
        assertFalse(budget.getName().isBlank());
        assertEquals(budget.getResourceName(), campaign.getCampaignBudget());

        // campaign: PAUSED SEARCH with the maximize-conversions strategy the plan carried
        assertEquals(CampaignStatus.PAUSED, campaign.getStatus());
        assertEquals(AdvertisingChannelType.SEARCH, campaign.getAdvertisingChannelType());
        assertTrue(campaign.hasMaximizeConversions());

        // ad group: PAUSED SEARCH_STANDARD, linked to the campaign by temp name
        assertEquals(AdGroupStatus.PAUSED, adGroup.getStatus());
        assertEquals(AdGroupType.SEARCH_STANDARD, adGroup.getType());
        assertEquals(campaign.getResourceName(), adGroup.getCampaign());

        // keywords + negatives: 2 positive (PAUSED) + 2 negative, all on the ad group's temp name
        assertEquals(2, criteria.stream().filter(c -> !c.getNegative()).count());
        assertEquals(2, criteria.stream().filter(AdGroupCriterion::getNegative).count());
        assertTrue(criteria.stream().filter(c -> !c.getNegative())
                .allMatch(c -> c.getStatus() == AdGroupCriterionStatus.PAUSED));
        assertTrue(criteria.stream().allMatch(c -> c.getAdGroup().equals(adGroup.getResourceName())));
        assertTrue(criteria.stream().anyMatch(c -> !c.getNegative()
                && c.getKeyword().getText().equals("2 bhk whitefield")
                && c.getKeyword().getMatchType() == KeywordMatchType.PHRASE));
        assertTrue(criteria.stream().anyMatch(c -> c.getNegative() && c.getKeyword().getText().equals("rent")));

        // RSA: PAUSED, points at the instrumented landing page, carries the compiled headlines/descriptions
        assertEquals(AdGroupAdStatus.PAUSED, adGroupAd.getStatus());
        assertEquals(adGroup.getResourceName(), adGroupAd.getAdGroup());
        assertTrue(adGroupAd.getAd().getFinalUrlsList().contains(LANDING_URL));
        assertEquals(3, adGroupAd.getAd().getResponsiveSearchAd().getHeadlinesList().size());
        assertEquals(2, adGroupAd.getAd().getResponsiveSearchAd().getDescriptionsList().size());
    }

    @Test
    void missingMccFailsFastBeforeTouchingTheFacade() {
        GoogleAdsClientFacade facade = mock(GoogleAdsClientFacade.class);
        GoogleLifecycle lifecycle = new GoogleLifecycle(facade, this.msg);

        // customer present, MCC (login-customer-id) absent
        Token noMcc = token(CUSTOMER_DASHED, null);
        assertThrows(GenericException.class, () -> lifecycle.launchPaused(compiledSearchCampaign(), noMcc));
        verifyNoInteractions(facade);
    }

    @Test
    void missingCustomerFailsFastBeforeTouchingTheFacade() {
        GoogleAdsClientFacade facade = mock(GoogleAdsClientFacade.class);
        GoogleLifecycle lifecycle = new GoogleLifecycle(facade, this.msg);

        // MCC present, operating customer-id absent
        Token noCustomer = token(null, MCC);
        assertThrows(GenericException.class, () -> lifecycle.launchPaused(compiledSearchCampaign(), noCustomer));
        verifyNoInteractions(facade);
    }
}
