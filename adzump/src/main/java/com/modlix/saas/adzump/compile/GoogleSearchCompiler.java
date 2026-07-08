package com.modlix.saas.adzump.compile;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Copy;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.KeywordSpec;
import com.modlix.saas.adzump.model.LandingPageRef;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.Targeting;
import com.modlix.saas.adzump.platform.CompiledCampaign;

/**
 * Google {@code SEARCH} {@link TypeCompiler} — the P1-first, fullest-control Google path. Builds the
 * campaign → ad group(s) → Responsive Search Ad(s) tree with keywords + negative keywords, all
 * PAUSED, as a {@code JsonNode} payload J4 maps onto Google Ads mutate operations. Google Search has
 * no native lead form, so the RSA's {@code finalUrl} is the instrumented landing page (A6) — the path
 * that also closes leadzump's website-attribution gap.
 *
 * <p>
 * Shape ported from the legacy {@code build_google_search_ad_payload.py} (campaign / budget / ad
 * group / keyword / RSA structure); the <b>hardcoded fallback ids and the account-currency ternary
 * were dropped</b> — money conversion is currency-driven ({@link MoneyUnits}) and a missing
 * customer/budget/landing-page is a J6 failure, not a default. RSA text is capped at Google's limits
 * (15 headlines / 4 descriptions). Google always uses a campaign-level budget, so
 * {@code EffectiveConfig.budgetMode} is not consulted here.
 * </p>
 */
@Component
public class GoogleSearchCompiler implements TypeCompiler {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** Google RSA hard caps. */
    private static final int MAX_HEADLINES = 15;
    private static final int MAX_DESCRIPTIONS = 4;

    @Override
    public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {

        CampaignPlanBody body = requireBody(plan);

        ObjectNode root = NODES.objectNode();
        root.set("campaign", buildCampaign(plan, body, cfg));

        ArrayNode adGroups = root.putArray("adGroups");
        for (AdGroup adGroup : googleAdGroups(body))
            adGroups.add(buildAdGroup(adGroup, body));

        return new CompiledCampaign(Platform.GOOGLE, CampaignType.SEARCH, plan.getName(), body.getObjective(), root);
    }

    // --- campaign -----------------------------------------------------------

    private ObjectNode buildCampaign(CampaignPlan plan, CampaignPlanBody body, EffectiveConfig cfg) {

        ObjectNode campaign = NODES.objectNode();
        campaign.put("name", plan.getName());
        campaign.put("advertisingChannelType", "SEARCH");
        campaign.put("status", "PAUSED");

        Money budget = body.getBudget() == null ? null : body.getBudget().getDailyBudget();
        if (budget == null)
            budget = body.getBudget() == null ? null : body.getBudget().getTotalBudget();
        if (budget == null)
            throw new IllegalStateException("Campaign budget is required for compilation (J6 gates this)");

        ObjectNode campaignBudget = campaign.putObject("campaignBudget");
        campaignBudget.put("amountMicros", MoneyUnits.toMicros(budget));
        campaignBudget.put("deliveryMethod", "STANDARD");
        campaignBudget.put("explicitlyShared", false);

        applyBidding(campaign, cfg);

        ObjectNode network = campaign.putObject("networkSettings");
        network.put("targetGoogleSearch", true);
        network.put("targetSearchNetwork", false);
        network.put("targetContentNetwork", false);
        network.put("targetPartnerSearchNetwork", false);

        ScheduleConfig schedule = body.getSchedule();
        if (schedule != null && schedule.getStartAt() != null)
            campaign.put("startDate", DateTimes.googleDate(schedule.getStartAt(), schedule.getTimezone()));
        if (schedule != null && schedule.getEndAt() != null)
            campaign.put("endDate", DateTimes.googleDate(schedule.getEndAt(), schedule.getTimezone()));

        return campaign;
    }

    private void applyBidding(ObjectNode campaign, EffectiveConfig cfg) {

        String neutral = cfg.biddingStrategy();
        if (neutral == null || neutral.isBlank())
            throw new IllegalStateException("Bidding strategy is required for compilation");

        switch (neutral.trim().toUpperCase()) {
            case "MAXIMIZE_CONVERSIONS" -> {
                ObjectNode strategy = campaign.putObject("maximizeConversions");
                if (cfg.targetCpa() != null)
                    strategy.put("targetCpaMicros", MoneyUnits.toMicros(cfg.targetCpa()));
            }
            case "TARGET_CPA" -> {
                if (cfg.targetCpa() == null)
                    throw new IllegalStateException("targetCpa is required for TARGET_CPA bidding");
                campaign.putObject("maximizeConversions").put("targetCpaMicros", MoneyUnits.toMicros(cfg.targetCpa()));
            }
            case "MAXIMIZE_CONVERSION_VALUE" -> campaign.putObject("maximizeConversionValue");
            case "MAXIMIZE_CLICKS", "TARGET_SPEND" -> campaign.putObject("targetSpend");
            default -> throw new IllegalStateException("Unsupported Google Search bidding strategy: " + neutral);
        }
    }

    // --- ad group -----------------------------------------------------------

    private ObjectNode buildAdGroup(AdGroup adGroup, CampaignPlanBody body) {

        ObjectNode node = NODES.objectNode();
        node.put("name", adGroup.getName());
        node.put("status", "PAUSED");
        node.put("type", "SEARCH_STANDARD");

        Targeting targeting = adGroup.getTargeting();

        ArrayNode keywords = node.putArray("keywords");
        if (targeting != null && targeting.getKeywords() != null)
            for (KeywordSpec keyword : targeting.getKeywords())
                keywords.add(buildKeyword(keyword, false));

        ArrayNode negatives = node.putArray("negativeKeywords");
        if (targeting != null && targeting.getNegativeKeywords() != null)
            for (KeywordSpec keyword : targeting.getNegativeKeywords())
                negatives.add(buildKeyword(keyword, true));

        ArrayNode ads = node.putArray("ads");
        if (adGroup.getAds() != null)
            for (Ad ad : adGroup.getAds())
                ads.add(buildRsa(ad, body));

        return node;
    }

    private ObjectNode buildKeyword(KeywordSpec keyword, boolean negative) {
        if (keyword.getText() == null || keyword.getText().isBlank())
            throw new IllegalStateException("Keyword text is required for compilation");
        ObjectNode node = NODES.objectNode();
        node.put("text", keyword.getText().trim());
        node.put("matchType", googleMatchType(keyword.getMatchType(), negative));
        return node;
    }

    // --- responsive search ad ----------------------------------------------

    private ObjectNode buildRsa(Ad ad, CampaignPlanBody body) {

        Creative creative = findCreative(body, ad.getCreativeId());
        Copy copy = creative == null ? null : creative.getCopy();

        List<String> headlines = firstNonEmpty(ad.getHeadlines(), copy == null ? null : copy.getHeadlines());
        List<String> descriptions = firstNonEmpty(ad.getDescriptions(), copy == null ? null : copy.getDescriptions());

        if (headlines == null || headlines.isEmpty())
            throw new IllegalStateException("RSA requires at least one headline (J6 gates the count)");
        if (descriptions == null || descriptions.isEmpty())
            throw new IllegalStateException("RSA requires at least one description (J6 gates the count)");

        ObjectNode node = NODES.objectNode();
        node.put("status", "PAUSED");
        node.putArray("finalUrls").add(resolveFinalUrl(ad, body));

        ObjectNode rsa = node.putObject("responsiveSearchAd");
        ArrayNode headlineNode = rsa.putArray("headlines");
        for (String headline : capped(headlines, MAX_HEADLINES))
            headlineNode.add(NODES.objectNode().put("text", headline));
        ArrayNode descriptionNode = rsa.putArray("descriptions");
        for (String description : capped(descriptions, MAX_DESCRIPTIONS))
            descriptionNode.add(NODES.objectNode().put("text", description));

        return node;
    }

    private static String resolveFinalUrl(Ad ad, CampaignPlanBody body) {
        if (ad.getFinalUrl() != null && !ad.getFinalUrl().isBlank())
            return ad.getFinalUrl();
        LandingPageRef landingPage = body.getLandingPage();
        if (landingPage != null && landingPage.getUrl() != null && !landingPage.getUrl().isBlank())
            return landingPage.getUrl();
        throw new IllegalStateException(
                "Google Search RSA requires a finalUrl (ad.finalUrl or landingPage.url); J6 gates this, "
                        + "no hardcoded fallback");
    }

    // --- neutral → Google enum mapping -------------------------------------

    private static String googleMatchType(MatchType matchType, boolean negative) {
        if (matchType == null) {
            if (negative)
                return "BROAD"; // negatives default to broad exclusion — the standard, safe shape
            throw new IllegalStateException("Keyword matchType is required for a positive keyword");
        }
        return switch (matchType) {
            case BROAD -> "BROAD";
            case PHRASE -> "PHRASE";
            case EXACT -> "EXACT";
        };
    }

    // --- plan helpers -------------------------------------------------------

    private static CampaignPlanBody requireBody(CampaignPlan plan) {
        if (plan == null || plan.getBody() == null)
            throw new IllegalStateException("CampaignPlan body is required for compilation");
        return plan.getBody();
    }

    private static List<AdGroup> googleAdGroups(CampaignPlanBody body) {
        if (body.getAdGroups() == null)
            return List.of();
        return body.getAdGroups().stream().filter(ag -> ag != null && ag.getPlatform() == Platform.GOOGLE).toList();
    }

    private static Creative findCreative(CampaignPlanBody body, String creativeId) {
        if (creativeId == null || body.getCreatives() == null)
            return null;
        for (Creative creative : body.getCreatives())
            if (creative != null && creativeId.equals(creative.getId()))
                return creative;
        return null;
    }

    private static List<String> firstNonEmpty(List<String> primary, List<String> fallback) {
        if (primary != null && !primary.isEmpty())
            return primary;
        return fallback;
    }

    private static List<String> capped(List<String> values, int max) {
        return values.size() <= max ? values : values.subList(0, max);
    }
}
