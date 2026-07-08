package com.modlix.saas.adzump.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.Bid;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.vertical.PolicyDefaults;

/**
 * Unit tests for the pure effective-config composition ({@link EffectiveConfig#of}) — the
 * campaign-override → account-default → vertical-default precedence (J7 §5.2) — and the
 * major→platform-unit money conversion ({@link MoneyUnits}). No DB, no registry: the three layers are
 * passed in directly.
 */
class EffectiveConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final PolicyDefaults VERTICAL = new PolicyDefaults(
            "30d_click", PolicyDefaults.BudgetMode.CAMPAIGN, "MAXIMIZE_CONVERSIONS", PlatformObjective.LEADS);

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    private static CampaignPlan planWith(CampaignPlanBody body) {
        return new CampaignPlan().setVertical("real_estate").setBody(body);
    }

    @Test
    void planStructuralValuesWinOverVerticalDefaults() {

        CampaignPlanBody body = new CampaignPlanBody()
                .setBudget(new BudgetPlan().setCurrency("INR").setDailyBudget(CompileFixtures.inr(3000)))
                .setSchedule(new ScheduleConfig().setTimezone("Asia/Kolkata"))
                .setCompliance(new Compliance().setSpecialAdCategory(SpecialAdCategory.HOUSING))
                .setObjective(new Objective().setPlatformObjective(PlatformObjective.CONVERSIONS)
                        .setTargetCostPerOutcome(CompileFixtures.inr(28000)))
                .setAdGroups(List.of(new AdGroup().setPlatform(Platform.GOOGLE)
                        .setBid(new Bid().setStrategy("TARGET_CPA"))));

        EffectiveConfig cfg = EffectiveConfig.of(planWith(body), null, null, VERTICAL);

        assertEquals("INR", cfg.currency());
        assertEquals("TARGET_CPA", cfg.biddingStrategy());          // plan ad-group bid over vertical
        assertEquals(PlatformObjective.CONVERSIONS, cfg.objective()); // plan objective over vertical
        assertEquals("30d_click", cfg.attributionWindow());          // only vertical carries it
        assertEquals(PolicyDefaults.BudgetMode.CAMPAIGN, cfg.budgetMode()); // from vertical
        assertEquals(SpecialAdCategory.HOUSING, cfg.specialAdCategory());
        assertEquals("Asia/Kolkata", cfg.timezone());
        assertEquals(CompileFixtures.inr(28000), cfg.targetCpa());
    }

    @Test
    void accountDefaultOverridesVerticalButNotThePlan() {

        PerformancePolicy accountPolicy = new PerformancePolicy()
                .setBody(json("{\"budgetMode\":\"AD_SET\",\"attributionWindow\":\"7d_click\","
                        + "\"biddingStrategy\":\"COST_CAP\"}"));

        // Plan carries no ad-group bid strategy, so the account default wins for bidding.
        CampaignPlanBody body = new CampaignPlanBody()
                .setBudget(new BudgetPlan().setCurrency("INR").setDailyBudget(CompileFixtures.inr(3000)))
                .setAdGroups(List.of(new AdGroup().setPlatform(Platform.META)));

        EffectiveConfig cfg = EffectiveConfig.of(planWith(body), accountPolicy, null, VERTICAL);

        assertEquals(PolicyDefaults.BudgetMode.AD_SET, cfg.budgetMode()); // account over vertical CAMPAIGN
        assertEquals("7d_click", cfg.attributionWindow());               // account over vertical 30d_click
        assertEquals("COST_CAP", cfg.biddingStrategy());                 // account over vertical
    }

    @Test
    void budgetModeDerivesFromPlanStructureWhenNoLayerSetsIt() {

        CampaignPlanBody abo = new CampaignPlanBody()
                .setAdGroups(List.of(new AdGroup().setPlatform(Platform.META).setBudget(CompileFixtures.inr(1000))));
        assertEquals(PolicyDefaults.BudgetMode.AD_SET,
                EffectiveConfig.of(planWith(abo), null, null, null).budgetMode());

        CampaignPlanBody cbo = new CampaignPlanBody()
                .setAdGroups(List.of(new AdGroup().setPlatform(Platform.META)));
        assertEquals(PolicyDefaults.BudgetMode.CAMPAIGN,
                EffectiveConfig.of(planWith(cbo), null, null, null).budgetMode());
    }

    @Test
    void nullLayersYieldNullAxesRatherThanInventedDefaults() {

        EffectiveConfig cfg = EffectiveConfig.of(planWith(new CampaignPlanBody()), null, null, null);
        assertNull(cfg.currency());
        assertNull(cfg.biddingStrategy());
        assertNull(cfg.attributionWindow());
        assertNull(cfg.objective());
    }

    @Test
    void moneyConvertsToMetaMinorUnitsByCurrency() {
        assertEquals(300000L, MoneyUnits.toMinorUnits(CompileFixtures.inr(3000)));      // INR: 2 digits
        assertEquals(1234L, MoneyUnits.toMinorUnits(new com.modlix.saas.adzump.model.Money(
                new java.math.BigDecimal("12.34"), "USD")));                            // USD: 2 digits
        assertEquals(1000L, MoneyUnits.toMinorUnits(new com.modlix.saas.adzump.model.Money(
                new java.math.BigDecimal("1000"), "JPY")));                             // JPY: 0 digits
    }

    @Test
    void moneyConvertsToGoogleMicros() {
        assertEquals(3_000_000_000L, MoneyUnits.toMicros(CompileFixtures.inr(3000)));
    }
}
