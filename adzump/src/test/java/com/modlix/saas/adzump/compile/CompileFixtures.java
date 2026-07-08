package com.modlix.saas.adzump.compile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.CreativeFormat;
import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.Audiences;
import com.modlix.saas.adzump.model.Bid;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Copy;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.Demographics;
import com.modlix.saas.adzump.model.Geo;
import com.modlix.saas.adzump.model.KeywordSpec;
import com.modlix.saas.adzump.model.LandingPageRef;
import com.modlix.saas.adzump.model.LeadForm;
import com.modlix.saas.adzump.model.LeadFormField;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.Targeting;
import com.modlix.saas.adzump.vertical.PolicyDefaults;

/**
 * Hand-built fixtures for the golden-payload compiler tests: one real-estate {@link CampaignPlan}
 * targeting both Google SEARCH and Meta LEADS, plus the resolved {@link EffectiveConfig} for each
 * platform. The plan mirrors the CONTRACT §5 worked example (Whitefield launch). Each factory returns
 * a fresh instance so a test can mutate one slot without leaking into another.
 */
final class CompileFixtures {

    static final String PAGE_ID = "1112223334";
    static final String META_ACCOUNT = "act_123456";
    static final String GOOGLE_ACCOUNT = "984-600-7422";
    static final String LEAD_FORM_ID = "lf-1";
    static final String LANDING_URL = "https://land.fincity.example/valmark-cityville?utm_source=google";
    static final String TZ = "Asia/Kolkata";
    static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 8, 1, 0, 0);

    private CompileFixtures() {
    }

    // ---- plan --------------------------------------------------------------

    /** RE plan targeting Google SEARCH + Meta LEADS, HOUSING compliance, ready to compile. */
    static CampaignPlan reSearchAndMetaLeads() {
        return new CampaignPlan()
                .setName("Whitefield Launch - Site Visits")
                .setProductId("prd_5521")
                .setVertical("real_estate")
                .setCampaignTypes(Map.of(Platform.GOOGLE, CampaignType.SEARCH, Platform.META, CampaignType.LEADS))
                .setBody(body());
    }

    private static CampaignPlanBody body() {
        return new CampaignPlanBody()
                .setObjective(new Objective()
                        .setPlatformObjective(PlatformObjective.LEADS)
                        .setTargetMilestone("SITE_VISIT")
                        .setTargetCostPerOutcome(inr(28000)))
                .setBudget(new BudgetPlan()
                        .setCurrency("INR")
                        .setDailyBudget(inr(3000)))
                .setSchedule(new ScheduleConfig()
                        .setStartAt(START)
                        .setEndAt(END)
                        .setTimezone(TZ))
                .setCompliance(new Compliance().setSpecialAdCategory(SpecialAdCategory.HOUSING))
                .setAdGroups(List.of(metaAdGroup(), googleAdGroup()))
                .setCreatives(List.of(metaCreative(), googleCreative()))
                .setLeadForm(leadForm())
                .setLandingPage(new LandingPageRef()
                        .setAppCode("landingpages")
                        .setPageName("valmark-cityville")
                        .setUrl(LANDING_URL)
                        .setInstrumented(true))
                .setLinks(new Links()
                        .setMeta(new Links.MetaLinks()
                                .setAdAccountId(META_ACCOUNT)
                                .setPageId(PAGE_ID)
                                .setPixelId("2223334445"))
                        .setGoogle(new Links.GoogleLinks().setAdAccountId(GOOGLE_ACCOUNT)));
    }

    private static AdGroup metaAdGroup() {
        return new AdGroup()
                .setId("ag_meta")
                .setName("Whitefield - end users")
                .setPlatform(Platform.META)
                .setBudget(inr(3000)) // AD_SET (ABO) budget lands on the ad set
                .setTargeting(new Targeting()
                        .setGeo(new Geo()
                                .setType("RADIUS")
                                .setCenter(new Geo.Center().setLat(12.97).setLng(77.75))
                                .setRadiusKm(8.0)
                                .setPlaces(new ArrayList<>(List.of("Whitefield", "Marathahalli"))))
                        .setDemographics(new Demographics().setAgeMin(25).setAgeMax(44)
                                .setGenders(List.of("ALL")))
                        .setAudiences(new Audiences().setInterests(List.of("6003629266583")))
                        .setLanguages(List.of("en", "kn")))
                .setAds(List.of(new Ad()
                        .setId("ad_meta")
                        .setName("Investment-ROI - interior render")
                        .setCreativeId("cr_meta")
                        .setLeadFormId(LEAD_FORM_ID)
                        .setCallToAction("BOOK_NOW")));
    }

    private static AdGroup googleAdGroup() {
        return new AdGroup()
                .setId("ag_google")
                .setName("Whitefield - intent")
                .setPlatform(Platform.GOOGLE)
                .setBid(new Bid().setStrategy("MAXIMIZE_CONVERSIONS").setTargetCpa(inr(28000)))
                .setTargeting(new Targeting()
                        .setKeywords(List.of(
                                new KeywordSpec().setText("2 bhk whitefield").setMatchType(MatchType.PHRASE),
                                new KeywordSpec().setText("apartments in whitefield").setMatchType(MatchType.BROAD)))
                        .setNegativeKeywords(List.of(
                                new KeywordSpec().setText("rent").setMatchType(MatchType.BROAD),
                                new KeywordSpec().setText("pg").setMatchType(MatchType.BROAD))))
                .setAds(List.of(new Ad()
                        .setId("ad_google")
                        .setName("RSA - Whitefield")
                        .setCreativeId("cr_google")
                        .setFinalUrl(LANDING_URL)
                        // 18 headlines / 6 descriptions: over Google's 15 / 4 caps, so the compiler
                        // must truncate to exactly 15 / 4.
                        .setHeadlines(seq("Headline", 18))
                        .setDescriptions(seq("Description", 6))));
    }

    private static Creative metaCreative() {
        return new Creative()
                .setId("cr_meta")
                .setFormat(CreativeFormat.IMAGE)
                .setCopy(new Copy()
                        .setPrimaryTexts(List.of("Assured-ROI homes in Whitefield"))
                        .setHeadlines(List.of("2 & 3 BHK in Whitefield"))
                        .setDescriptions(List.of("RERA-approved. Site visits open."))
                        .setCta("BOOK_NOW"));
    }

    private static Creative googleCreative() {
        return new Creative()
                .setId("cr_google")
                .setFormat(CreativeFormat.RSA)
                .setCopy(new Copy()
                        .setHeadlines(seq("Copy headline", 3))
                        .setDescriptions(seq("Copy description", 2)));
    }

    private static LeadForm leadForm() {
        return new LeadForm()
                .setId(LEAD_FORM_ID)
                .setPlatform(Platform.META)
                .setPrivacyPolicyUrl("https://fincity.example/privacy")
                .setThankYouMessage("We'll call you to schedule a site visit.")
                .setFields(List.of(
                        new LeadFormField().setKey("full_name").setType("FULL_NAME"),
                        new LeadFormField().setKey("phone").setType("PHONE"),
                        new LeadFormField().setKey("email").setType("EMAIL"),
                        new LeadFormField().setKey("budget").setType("CUSTOM")
                                .setOptions(List.of("<80L", "80L-1.2Cr", ">1.2Cr"))));
    }

    // ---- effective config --------------------------------------------------

    /** Resolved effective config for the Meta LEADS compile (RE defaults: AD_SET budget, HOUSING). */
    static EffectiveConfig metaConfig() {
        return new EffectiveConfig("INR", PolicyDefaults.BudgetMode.AD_SET, "MAXIMIZE_CONVERSIONS", null,
                "7d_click_1d_view", PlatformObjective.LEADS, SpecialAdCategory.HOUSING, TZ);
    }

    /** Resolved effective config for the Google SEARCH compile. */
    static EffectiveConfig googleConfig() {
        return new EffectiveConfig("INR", PolicyDefaults.BudgetMode.AD_SET, "MAXIMIZE_CONVERSIONS", null,
                "30d_click", PlatformObjective.LEADS, SpecialAdCategory.HOUSING, TZ);
    }

    // ---- helpers -----------------------------------------------------------

    static Money inr(long amount) {
        return new Money(new BigDecimal(amount), "INR");
    }

    private static List<String> seq(String prefix, int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 1; i <= count; i++)
            out.add(prefix + " " + i);
        return out;
    }
}
