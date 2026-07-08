package com.modlix.saas.adzump.validate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.AssetGroup;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.KeywordSpec;
import com.modlix.saas.adzump.model.LeadForm;
import com.modlix.saas.adzump.model.LeadFormField;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.Targeting;

/**
 * Hand-built {@link CampaignPlan} fixtures for the J6 rule-layer tests. Each {@code valid*} factory
 * returns a plan that passes ALL five layers under a permissive (empty fetched-id) context and a null
 * (generic) vertical; individual tests mutate one slot to provoke exactly one violation code.
 */
final class Plans {

    private Plans() {
    }

    static final String LEAD_FORM_ID = "lf-1";

    // ---- valid whole plans (generic vertical, referential-permissive ctx) ----

    /** Google Search: adGroups + keywords + RSA ads (3 headlines / 2 descriptions). */
    static CampaignPlan validSearch() {
        return base(Map.of(Platform.GOOGLE, CampaignType.SEARCH))
                .setBody(baseBody()
                        .setAdGroups(List.of(googleSearchAdGroup())));
    }

    /** Google PMax: one asset group meeting the asset minimums. */
    static CampaignPlan validPmax() {
        return base(Map.of(Platform.GOOGLE, CampaignType.PMAX))
                .setBody(baseBody()
                        .setAssetGroups(List.of(pmaxAssetGroup(Platform.GOOGLE))));
    }

    /** Meta lead gen: ad set + creative + lead form (ad references the plan's lead form). */
    static CampaignPlan validMetaLeads() {
        return base(Map.of(Platform.META, CampaignType.LEADS))
                .setBody(baseBody()
                        .setAdGroups(List.of(metaLeadAdGroup()))
                        .setCreatives(List.of(new Creative().setId("cr-1")))
                        .setLeadForm(leadForm(Platform.META)));
    }

    // ---- reusable pieces ----

    static CampaignPlan base(Map<Platform, CampaignType> types) {
        return new CampaignPlan()
                .setName("Spring launch")
                .setProductId("prod-1")
                .setCampaignTypes(types);
    }

    static CampaignPlanBody baseBody() {
        return new CampaignPlanBody()
                .setObjective(new Objective())
                .setBudget(dailyBudget())
                .setSchedule(sensibleSchedule());
    }

    static BudgetPlan dailyBudget() {
        return new BudgetPlan().setCurrency("USD").setDailyBudget(money(50));
    }

    static ScheduleConfig sensibleSchedule() {
        return new ScheduleConfig()
                .setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .setEndAt(LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    static Money money(int amount) {
        return new Money(BigDecimal.valueOf(amount), "USD");
    }

    static AdGroup googleSearchAdGroup() {
        return new AdGroup()
                .setName("ag-google")
                .setPlatform(Platform.GOOGLE)
                .setTargeting(new Targeting().setKeywords(List.of(new KeywordSpec().setText("buy home"))))
                .setAds(List.of(rsaAd()));
    }

    static AdGroup metaLeadAdGroup() {
        return new AdGroup()
                .setName("ag-meta")
                .setPlatform(Platform.META)
                .setTargeting(new Targeting())
                .setAds(List.of(new Ad().setName("ad-1").setLeadFormId(LEAD_FORM_ID)));
    }

    static Ad rsaAd() {
        return new Ad()
                .setName("rsa-1")
                .setHeadlines(List.of("H1", "H2", "H3"))
                .setDescriptions(List.of("D1", "D2"));
    }

    static AssetGroup pmaxAssetGroup(Platform p) {
        return new AssetGroup()
                .setId("asg-1")
                .setPlatform(p)
                .setHeadlines(List.of("H1", "H2", "H3"))
                .setDescriptions(List.of("D1", "D2"))
                .setImages(List.of("img-1"));
    }

    static LeadForm leadForm(Platform p) {
        return new LeadForm()
                .setId(LEAD_FORM_ID)
                .setPlatform(p)
                .setFields(List.of(new LeadFormField().setKey("email").setType("EMAIL")));
    }

    static Compliance housing() {
        return new Compliance().setSpecialAdCategory(SpecialAdCategory.HOUSING);
    }

    // ---- assertion helpers ----

    static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::getCode).toList();
    }

    static boolean has(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(i -> code.equals(i.getCode()));
    }

    static ValidationIssue find(List<ValidationIssue> issues, String code) {
        return issues.stream().filter(i -> code.equals(i.getCode())).findFirst().orElse(null);
    }
}
