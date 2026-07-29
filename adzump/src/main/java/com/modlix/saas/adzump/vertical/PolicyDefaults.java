package com.modlix.saas.adzump.vertical;

import com.modlix.saas.adzump.enums.PlatformObjective;

/**
 * The per-{@link com.modlix.saas.adzump.enums.CampaignType} default policy values a vertical supplies.
 *
 * <p>These are the values the platform compilers (J3 Meta / J4 Google) are <b>forbidden to hardcode</b>
 * (the legacy quality bug was silent hardcoded fallbacks in the payload layer). They live here as
 * <i>vertical knowledge</i>. J7 resolves the effective value for a campaign as
 * <b>campaign-override &rarr; account-default &rarr; vertical-default</b> and feeds it into
 * {@code EffectiveConfig}; J3/J4 only execute what {@code EffectiveConfig} hands them. A missing
 * <i>required</i> value is a J6 failure, never a silent platform default.
 *
 * <p>Fields map 1:1 onto the J7 {@code EffectiveConfig} axes (attributionWindow, budgetMode,
 * biddingStrategy, objective mapping).
 *
 * @param attributionWindow platform-neutral attribution window token, e.g. {@code "7d_click_1d_view"}
 *                          or {@code "30d_click"}; the platform compiler maps it to its own enum.
 * @param budgetMode        where the budget lives — campaign-level (CBO) vs ad-set/ad-group level.
 * @param biddingStrategy   platform-neutral bidding-strategy token, e.g. {@code "MAXIMIZE_CONVERSIONS"},
 *                          {@code "TARGET_CPA"}; the platform compiler maps it to its own enum.
 * @param objective         the platform objective this campaign type maps to (the "objective mapping").
 */
public record PolicyDefaults(
        String attributionWindow,
        BudgetMode budgetMode,
        String biddingStrategy,
        PlatformObjective objective) {

    /** Where the budget is optimized: campaign-level (CBO) or the ad-set / ad-group level. */
    public enum BudgetMode {
        CAMPAIGN,
        AD_SET
    }
}
