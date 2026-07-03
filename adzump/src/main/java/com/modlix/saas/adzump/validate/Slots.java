package com.modlix.saas.adzump.validate;

import static com.modlix.saas.adzump.validate.ValidationSupport.isBlank;
import static com.modlix.saas.adzump.validate.ValidationSupport.notEmpty;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;

/**
 * The type-aware required-slot vocabulary and the "is this slot filled?" predicate. Single source of
 * truth so {@link StructuralRules} (the ERROR-emitting gate) and completeness (the advisory rail) can
 * never disagree about what a plan of a given {@code CampaignType} needs.
 *
 * <p>TODO(J5): the per-type slot sets are J6 built-in fallbacks. Once the vertical registry lands they
 * come from {@code VerticalRegistry.get(vertical).requiredSlots(type)} (mapped to these slot keys), so
 * the required structure is vertical- and type-aware and no structural default is hardcoded here.
 */
public final class Slots {

    public static final String NAME = "name";
    public static final String PRODUCT_ID = "productId";
    public static final String OBJECTIVE = "objective";
    public static final String BUDGET = "budget";
    public static final String SCHEDULE = "schedule";
    public static final String AD_GROUPS = "adGroups";
    public static final String KEYWORDS = "keywords";
    public static final String ASSET_GROUPS = "assetGroups";
    public static final String CREATIVES = "creatives";
    public static final String LEAD_FORM = "leadForm";
    public static final String LANDING_PAGE = "landingPage";

    /** Slots every plan needs regardless of platform/type. */
    private static final List<String> BASE = List.of(NAME, PRODUCT_ID, OBJECTIVE, BUDGET, SCHEDULE);

    private Slots() {
    }

    /**
     * The union of required slots for a plan: {@link #BASE} plus each targeted {@code CampaignType}'s
     * contribution. {@code campaignTypes} is a {@code Map<Platform,CampaignType>} (one type per
     * platform), so a Meta-LEADS + Google-SEARCH plan requires the union of both.
     */
    public static Set<String> forPlan(CampaignPlan plan) {

        Set<String> req = new LinkedHashSet<>(BASE);

        Map<Platform, CampaignType> types = plan == null ? null : plan.getCampaignTypes();
        if (types != null)
            for (CampaignType t : types.values())
                req.addAll(forType(t));

        return req;
    }

    /** The required-slot contribution of a single {@code CampaignType} (J6 fallback; see class TODO). */
    public static Set<String> forType(CampaignType type) {

        if (type == null)
            return Set.of();

        return switch (type) {
            case SEARCH -> Set.of(AD_GROUPS, KEYWORDS);
            case DSA, SHOPPING, APP -> Set.of(AD_GROUPS);
            case DISPLAY, VIDEO, DEMAND_GEN -> Set.of(AD_GROUPS, CREATIVES);
            case PMAX, ADVANTAGE_PLUS -> Set.of(ASSET_GROUPS);
            case LEADS -> Set.of(AD_GROUPS, LEAD_FORM, CREATIVES);
            case SALES, TRAFFIC, AWARENESS, ENGAGEMENT -> Set.of(AD_GROUPS, CREATIVES);
        };
    }

    /** Whether a given required slot is satisfied by the plan. Null-safe over the whole plan tree. */
    public static boolean filled(String slot, CampaignPlan plan) {

        CampaignPlanBody body = plan == null ? null : plan.getBody();

        return switch (slot) {
            case NAME -> plan != null && !isBlank(plan.getName());
            case PRODUCT_ID -> plan != null && !isBlank(plan.getProductId());
            case OBJECTIVE -> body != null && body.getObjective() != null;
            case BUDGET -> body != null && body.getBudget() != null;
            case SCHEDULE -> body != null && body.getSchedule() != null;
            case AD_GROUPS -> body != null && notEmpty(body.getAdGroups());
            case ASSET_GROUPS -> body != null && notEmpty(body.getAssetGroups());
            case CREATIVES -> body != null && notEmpty(body.getCreatives());
            case LEAD_FORM -> body != null && body.getLeadForm() != null;
            case LANDING_PAGE -> body != null && body.getLandingPage() != null;
            case KEYWORDS -> body != null && hasAnyKeyword(body);
            default -> true;
        };
    }

    /** JSON-pointer address for a slot, so issues are machine-addressable (A3 repair / eval asserts). */
    public static String path(String slot) {
        return switch (slot) {
            case NAME -> "/name";
            case PRODUCT_ID -> "/productId";
            case OBJECTIVE -> "/body/objective";
            case BUDGET -> "/body/budget";
            case SCHEDULE -> "/body/schedule";
            case AD_GROUPS -> "/body/adGroups";
            case ASSET_GROUPS -> "/body/assetGroups";
            case CREATIVES -> "/body/creatives";
            case LEAD_FORM -> "/body/leadForm";
            case LANDING_PAGE -> "/body/landingPage";
            case KEYWORDS -> "/body/adGroups";
            default -> "/";
        };
    }

    private static boolean hasAnyKeyword(CampaignPlanBody body) {
        if (body.getAdGroups() == null)
            return false;
        for (AdGroup ag : body.getAdGroups())
            if (ag != null && ag.getTargeting() != null && notEmpty(ag.getTargeting().getKeywords()))
                return true;
        return false;
    }
}
