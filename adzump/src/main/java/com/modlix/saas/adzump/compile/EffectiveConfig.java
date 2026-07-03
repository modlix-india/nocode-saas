package com.modlix.saas.adzump.compile;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.vertical.PolicyDefaults;

/**
 * The compiler's resolved settings for one campaign — the single object every {@link TypeCompiler}
 * reads instead of reaching for a constant. Each axis is resolved through the precedence chain
 * <b>campaign-override → account-default → vertical-default</b> (CONTRACT §0 / J7 §5.2). This is why
 * the platform modules (J3/J4) hold <b>no</b> defaults: the value a compiler writes for, say, the
 * Meta budget mode comes from here, never from a literal in the platform code (the legacy quality
 * bug). A <i>required</i> field with no value anywhere is impossible at compile time because J6 gates
 * it upstream; where a genuinely required value is still absent, a compiler throws rather than
 * inventing one.
 *
 * <p>
 * The record is a pure value. Build it either with the canonical constructor (unit tests do this to
 * stay independent of the DB / registry) or via {@link #of} (the {@link EffectiveConfigResolver}
 * fetches the three layers and composes them).
 * </p>
 *
 * <p>
 * Axes mirror J5's {@link PolicyDefaults} 1:1 (attributionWindow, budgetMode, biddingStrategy,
 * objective) plus the plan-derived currency, compliance category and timezone the compilers need.
 * The neutral {@code biddingStrategy} / {@code attributionWindow} tokens are mapped to each
 * platform's enum by the {@link TypeCompiler}.
 * </p>
 *
 * @param currency          plan/account currency (ISO-4217) used for money conversion
 * @param budgetMode        {@link PolicyDefaults.BudgetMode#CAMPAIGN} (Meta CBO / Google campaign
 *                          budget) vs {@link PolicyDefaults.BudgetMode#AD_SET} (Meta ABO); load-bearing
 *                          for Meta, ignored by Google Search which always uses a campaign budget
 * @param biddingStrategy   neutral bidding-strategy token (e.g. {@code MAXIMIZE_CONVERSIONS},
 *                          {@code TARGET_CPA}); the compiler maps it to the platform enum
 * @param targetCpa         target cost per outcome, when the strategy is CPA/cap-based (nullable)
 * @param attributionWindow neutral attribution-window token (e.g. {@code 7d_click_1d_view},
 *                          {@code 30d_click}); Meta maps it to {@code attribution_spec} (nullable)
 * @param objective         resolved neutral platform objective (the "objective mapping")
 * @param specialAdCategory resolved compliance category; {@link SpecialAdCategory#HOUSING} stamps the
 *                          Meta {@code special_ad_categories} and the restricted targeting shape
 *                          (nullable / {@code NONE} ⇒ none)
 * @param timezone          IANA account timezone for schedule formatting
 */
public record EffectiveConfig(
        String currency,
        PolicyDefaults.BudgetMode budgetMode,
        String biddingStrategy,
        Money targetCpa,
        String attributionWindow,
        PlatformObjective objective,
        SpecialAdCategory specialAdCategory,
        String timezone) {

    /**
     * Composes the three resolution layers into the effective config (pure). The precedence for each
     * axis is <b>campaign-override → account-default → vertical-default</b>; where the plan carries
     * the value structurally (bid strategy on an ad group, per-group budgets, objective, compliance,
     * schedule tz) that is the campaign layer.
     *
     * @param plan            the validated plan (its {@code body.*Override} blocks are the campaign
     *                        override layer)
     * @param accountPolicy   the account-default performance policy (its JSON body may carry
     *                        {@code budgetMode} / {@code biddingStrategy} / {@code attributionWindow});
     *                        nullable
     * @param accountAutonomy accepted for the resolution contract; the compiled payload does not
     *                        depend on autonomy (a loop-only concern), so it is not read here
     * @param verticalDefaults the J5 vertical defaults for this campaign type; nullable
     */
    public static EffectiveConfig of(CampaignPlan plan,
            PerformancePolicy accountPolicy,
            AutonomyConfig accountAutonomy,
            PolicyDefaults verticalDefaults) {

        CampaignPlanBody body = plan == null ? null : plan.getBody();

        JsonNode accountPolicyBody = accountPolicy == null ? null : accountPolicy.getBody();
        JsonNode planPolicyOverride = body == null ? null : body.getPerformancePolicyOverride();
        JsonNode planAutonomyOverride = body == null ? null : body.getAutonomyOverride();

        String currency = body != null && body.getBudget() != null ? body.getBudget().getCurrency() : null;

        PolicyDefaults.BudgetMode budgetMode = resolveBudgetMode(body, planPolicyOverride, planAutonomyOverride,
                accountPolicyBody, verticalDefaults);

        String biddingStrategy = firstNonBlank(
                planBidStrategy(body),
                jsonText(planPolicyOverride, "biddingStrategy"),
                jsonText(accountPolicyBody, "biddingStrategy"),
                verticalDefaults == null ? null : verticalDefaults.biddingStrategy());

        Money targetCpa = planTargetCpa(body);

        String attributionWindow = firstNonBlank(
                jsonText(planPolicyOverride, "attributionWindow"),
                jsonText(accountPolicyBody, "attributionWindow"),
                verticalDefaults == null ? null : verticalDefaults.attributionWindow());

        PlatformObjective objective = body != null && body.getObjective() != null
                && body.getObjective().getPlatformObjective() != null
                        ? body.getObjective().getPlatformObjective()
                        : (verticalDefaults == null ? null : verticalDefaults.objective());

        SpecialAdCategory specialAdCategory = body != null && body.getCompliance() != null
                ? body.getCompliance().getSpecialAdCategory()
                : null;

        String timezone = body != null && body.getSchedule() != null ? body.getSchedule().getTimezone() : null;

        return new EffectiveConfig(currency, budgetMode, biddingStrategy, targetCpa, attributionWindow, objective,
                specialAdCategory, timezone);
    }

    // --- resolution helpers -------------------------------------------------

    private static PolicyDefaults.BudgetMode resolveBudgetMode(CampaignPlanBody body, JsonNode planPolicyOverride,
            JsonNode planAutonomyOverride, JsonNode accountPolicyBody, PolicyDefaults verticalDefaults) {

        String explicit = firstNonBlank(
                jsonText(planPolicyOverride, "budgetMode"),
                jsonText(planAutonomyOverride, "budgetMode"),
                jsonText(accountPolicyBody, "budgetMode"));
        if (explicit != null)
            return PolicyDefaults.BudgetMode.valueOf(explicit.trim().toUpperCase());

        if (verticalDefaults != null && verticalDefaults.budgetMode() != null)
            return verticalDefaults.budgetMode();

        // Last resort: derive from plan structure — an ad group carrying its own budget ⇒ AD_SET,
        // all-null ⇒ CAMPAIGN. (Not a business default; a structural read of the plan.)
        List<AdGroup> adGroups = body == null ? null : body.getAdGroups();
        if (adGroups != null)
            for (AdGroup ag : adGroups)
                if (ag != null && ag.getBudget() != null)
                    return PolicyDefaults.BudgetMode.AD_SET;

        return PolicyDefaults.BudgetMode.CAMPAIGN;
    }

    private static String planBidStrategy(CampaignPlanBody body) {
        if (body == null || body.getAdGroups() == null)
            return null;
        for (AdGroup ag : body.getAdGroups())
            if (ag != null && ag.getBid() != null && ag.getBid().getStrategy() != null
                    && !ag.getBid().getStrategy().isBlank())
                return ag.getBid().getStrategy();
        return null;
    }

    private static Money planTargetCpa(CampaignPlanBody body) {
        if (body == null)
            return null;
        if (body.getObjective() != null && body.getObjective().getTargetCostPerOutcome() != null)
            return body.getObjective().getTargetCostPerOutcome();
        if (body.getAdGroups() != null)
            for (AdGroup ag : body.getAdGroups())
                if (ag != null && ag.getBid() != null && ag.getBid().getTargetCpa() != null)
                    return ag.getBid().getTargetCpa();
        return null;
    }

    private static String jsonText(JsonNode node, String field) {
        if (node == null)
            return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.isTextual() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }
}
