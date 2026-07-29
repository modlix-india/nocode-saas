package com.modlix.saas.adzump.vertical;

/**
 * The set of plan "slots" a {@link VerticalPlaybook} can declare required for a given
 * {@link com.modlix.saas.adzump.enums.CampaignType}. This is the vocabulary A1's completeness rail
 * and J6's validator speak in: {@code requiredSlots(type)} minus the slots a plan fills = what is
 * still missing.
 *
 * <p>Each constant carries a stable {@link #key()} string that matches the field/path name used in
 * the plan body + the P0 completeness map (see {@code CampaignPlanService.completeness}), so J6 can
 * map a {@code Slot} onto a concrete plan field without any per-type hardcoding.
 */
public enum Slot {

    NAME("name"),
    PRODUCT("productId"),
    OBJECTIVE("objective"),
    BUDGET("budget"),
    SCHEDULE("schedule"),
    GEO("geo"),
    AUDIENCE("audience"),
    KEYWORDS("keywords"),
    AD_GROUPS("adGroups"),
    ASSET_GROUPS("assetGroups"),
    CREATIVES("creatives"),
    LANDING_PAGE("landingPage"),
    LEAD_FORM("leadForm");

    private final String key;

    Slot(String key) {
        this.key = key;
    }

    /** Stable plan-field / completeness key (camelCase), e.g. {@code productId}, {@code assetGroups}. */
    public String key() {
        return key;
    }
}
