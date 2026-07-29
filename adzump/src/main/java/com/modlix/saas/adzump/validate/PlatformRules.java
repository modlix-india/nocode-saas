package com.modlix.saas.adzump.validate;

import static com.modlix.saas.adzump.validate.ValidationSupport.sizeOf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.AssetGroup;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;

/**
 * Layer 3 — <b>platform</b>. Per {@code (Platform, CampaignType)}: is the type supported on the platform;
 * Google RSA needs &ge;3 headlines / &ge;2 descriptions; PMax / Advantage+ needs &ge;1 asset group meeting
 * Google's asset minimums; Meta lead-gen must carry the special ad category the vertical requires.
 *
 * <p>{@code campaignTypes} is a {@code Map<Platform,CampaignType>} (one type per platform), so each rule
 * runs at most once per platform. All findings are {@code ERROR}.
 *
 * <p>TODO(J2b): the {@link #SUPPORTED} table and RSA/PMax minimums are J6 fallbacks. Once the platform
 * SPI lands they come from {@code AdPlatformRegistry.get(platform).capabilities()} (supported types +
 * per-type {@code OptimizationProfile}), so J7/J8 and J6 branch on the same data, not two copies.
 */
public final class PlatformRules {

    public static final String PLATFORM_TYPE_UNSUPPORTED = "PLATFORM_TYPE_UNSUPPORTED";
    public static final String PLATFORM_RSA_HEADLINES = "PLATFORM_RSA_HEADLINES";
    public static final String PLATFORM_RSA_DESCRIPTIONS = "PLATFORM_RSA_DESCRIPTIONS";
    public static final String PLATFORM_PMAX_NO_ASSET_GROUP = "PLATFORM_PMAX_NO_ASSET_GROUP";
    public static final String PLATFORM_PMAX_ASSET_MINIMUMS = "PLATFORM_PMAX_ASSET_MINIMUMS";
    public static final String PLATFORM_META_HOUSING_REQUIRED = "PLATFORM_META_HOUSING_REQUIRED";

    private static final int RSA_MIN_HEADLINES = 3;
    private static final int RSA_MIN_DESCRIPTIONS = 2;
    private static final int ASSET_MIN_IMAGES = 1;

    /** J2b fallback: which campaign types each platform supports (J2b spec §5.4). */
    private static final Map<Platform, Set<CampaignType>> SUPPORTED = new EnumMap<>(Platform.class);

    static {
        SUPPORTED.put(Platform.GOOGLE, Set.of(CampaignType.SEARCH, CampaignType.PMAX, CampaignType.DEMAND_GEN,
                CampaignType.DISPLAY, CampaignType.VIDEO, CampaignType.APP, CampaignType.SHOPPING, CampaignType.DSA));
        SUPPORTED.put(Platform.META, Set.of(CampaignType.LEADS, CampaignType.SALES, CampaignType.TRAFFIC,
                CampaignType.AWARENESS, CampaignType.ENGAGEMENT, CampaignType.APP, CampaignType.ADVANTAGE_PLUS));
    }

    private PlatformRules() {
    }

    public static List<ValidationIssue> check(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();

        Map<Platform, CampaignType> types = plan == null ? null : plan.getCampaignTypes();
        if (types == null || types.isEmpty())
            return issues; // structural layer flags the absent type

        CampaignPlanBody body = plan.getBody();

        for (Map.Entry<Platform, CampaignType> e : types.entrySet()) {
            Platform p = e.getKey();
            CampaignType t = e.getValue();

            if (!supports(p, t))
                issues.add(ValidationIssue.error(PLATFORM_TYPE_UNSUPPORTED, "/campaignTypes/" + p, "campaignTypes",
                        t + " is not a supported campaign type on " + p));

            if (p == Platform.META && t == CampaignType.LEADS && VerticalPolicy.requiresHousing(ctx.vertical())) {
                SpecialAdCategory sac = specialCategory(body);
                if (sac != SpecialAdCategory.HOUSING)
                    issues.add(ValidationIssue.error(PLATFORM_META_HOUSING_REQUIRED,
                            "/body/compliance/specialAdCategory", "specialAdCategory",
                            "Meta lead-gen for this vertical must declare the HOUSING special ad category"));
            }

            if (p == Platform.GOOGLE && (t == CampaignType.SEARCH || t == CampaignType.DSA))
                checkRsa(issues, body);

            if (t == CampaignType.PMAX || t == CampaignType.ADVANTAGE_PLUS)
                checkPmax(issues, body, p);
        }

        return issues;
    }

    private static void checkRsa(List<ValidationIssue> issues, CampaignPlanBody body) {
        if (body == null || body.getAdGroups() == null)
            return;
        List<AdGroup> adGroups = body.getAdGroups();
        for (int i = 0; i < adGroups.size(); i++) {
            AdGroup ag = adGroups.get(i);
            if (ag == null || ag.getPlatform() == Platform.META || ag.getAds() == null)
                continue; // only Google ad groups carry RSA
            for (int j = 0; j < ag.getAds().size(); j++) {
                Ad ad = ag.getAds().get(j);
                if (ad == null)
                    continue;
                if (sizeOf(ad.getHeadlines()) < RSA_MIN_HEADLINES)
                    issues.add(ValidationIssue.error(PLATFORM_RSA_HEADLINES,
                            "/body/adGroups/" + i + "/ads/" + j + "/headlines", "headlines",
                            "Google RSA requires at least " + RSA_MIN_HEADLINES + " headlines"));
                if (sizeOf(ad.getDescriptions()) < RSA_MIN_DESCRIPTIONS)
                    issues.add(ValidationIssue.error(PLATFORM_RSA_DESCRIPTIONS,
                            "/body/adGroups/" + i + "/ads/" + j + "/descriptions", "descriptions",
                            "Google RSA requires at least " + RSA_MIN_DESCRIPTIONS + " descriptions"));
            }
        }
    }

    private static void checkPmax(List<ValidationIssue> issues, CampaignPlanBody body, Platform p) {

        List<AssetGroup> groups = body == null ? null : body.getAssetGroups();
        List<AssetGroup> forPlatform = new ArrayList<>();
        if (groups != null)
            for (AssetGroup g : groups)
                if (g != null && (g.getPlatform() == null || g.getPlatform() == p))
                    forPlatform.add(g);

        if (forPlatform.isEmpty()) {
            issues.add(ValidationIssue.error(PLATFORM_PMAX_NO_ASSET_GROUP, "/body/assetGroups", "assetGroups",
                    "PMax / Advantage+ on " + p + " requires at least one asset group"));
            return;
        }

        for (int i = 0; i < forPlatform.size(); i++) {
            AssetGroup g = forPlatform.get(i);
            if (sizeOf(g.getHeadlines()) < RSA_MIN_HEADLINES)
                issues.add(ValidationIssue.error(PLATFORM_PMAX_ASSET_MINIMUMS,
                        "/body/assetGroups/" + i + "/headlines", "headlines",
                        "PMax asset group requires at least " + RSA_MIN_HEADLINES + " headlines"));
            if (sizeOf(g.getDescriptions()) < RSA_MIN_DESCRIPTIONS)
                issues.add(ValidationIssue.error(PLATFORM_PMAX_ASSET_MINIMUMS,
                        "/body/assetGroups/" + i + "/descriptions", "descriptions",
                        "PMax asset group requires at least " + RSA_MIN_DESCRIPTIONS + " descriptions"));
            if (sizeOf(g.getImages()) < ASSET_MIN_IMAGES)
                issues.add(ValidationIssue.error(PLATFORM_PMAX_ASSET_MINIMUMS,
                        "/body/assetGroups/" + i + "/images", "images",
                        "PMax asset group requires at least " + ASSET_MIN_IMAGES + " image"));
        }
    }

    private static SpecialAdCategory specialCategory(CampaignPlanBody body) {
        Compliance c = body == null ? null : body.getCompliance();
        return c == null ? null : c.getSpecialAdCategory();
    }

    /** J2b fallback support check. TODO(J2b): replace with {@code capabilities().supports(type)}. */
    public static boolean supports(Platform p, CampaignType t) {
        Set<CampaignType> set = SUPPORTED.get(p);
        return set != null && set.contains(t);
    }
}
