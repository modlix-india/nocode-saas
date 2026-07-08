package com.modlix.saas.adzump.vertical.realestate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.vertical.AttributeTaxonomy;
import com.modlix.saas.adzump.vertical.ComplianceRule;
import com.modlix.saas.adzump.vertical.CriticRubric;
import com.modlix.saas.adzump.vertical.PolicyDefaults;
import com.modlix.saas.adzump.vertical.ProxyWeights;
import com.modlix.saas.adzump.vertical.Slot;
import com.modlix.saas.adzump.vertical.TargetingSeeds;
import com.modlix.saas.adzump.vertical.VerticalPlaybook;

/**
 * The real-estate playbook — the first <i>tuned</i> vertical. This is the single home for all RE
 * industry knowledge that the legacy scattered as hardcoded {@code _REAL_ESTATE_*} constants, the
 * currency ternary, the inline HOUSING handling, and fixed milestone names. Nothing RE-specific lives
 * outside this package.
 *
 * <p>Content is authored as code constants for P1. TODO(J5 open question §9.1): a later slice may load
 * this from a per-vertical data file (YAML/JSON) with a schema check so non-engineers can tune it; the
 * {@link VerticalPlaybook} seam is stable across that change.
 */
@Component
public class RealEstatePlaybook implements VerticalPlaybook {

    public static final String CODE = "real_estate";

    // ── Attribute taxonomy (J20): the RE axes A4 tags creatives on and J20 attributes outcomes to.
    // Axes/values ported from CONTRACT §1.3 (creative attributes).
    private static final AttributeTaxonomy TAXONOMY = buildTaxonomy();

    // ── HOUSING compliance (Meta special-ad-category + Google restricted housing category). Real
    // estate is inherently housing, so this applies to every platform/type for this vertical.
    private static final ComplianceRule HOUSING_RULE = new ComplianceRule(
            SpecialAdCategory.HOUSING,
            "Real-estate ads fall under the HOUSING special-ad category: age, gender, ZIP-radius and "
                    + "detailed-interest narrowing are restricted, and a compliance disclaimer "
                    + "(e.g. RERA registration) must be present.",
            List.of("demographics.ageMin", "demographics.ageMax", "demographics.genders",
                    "geo.radius", "audiences.interests", "audiences.lookalikeIds"),
            true);

    // ── Critic rubric (A3): the shared base axes plus RE overlays (compliance safety, local relevance).
    private static final CriticRubric RUBRIC = new CriticRubric(List.of(
            new CriticRubric.Axis("targeting_coherence",
                    "Catchment geo, language and keyword/interest choices fit the project location and buyer", 0.22),
            new CriticRubric.Axis("structure_fit",
                    "Campaign structure matches the type (search->ad groups+keywords, PMax->asset groups)", 0.15),
            new CriticRubric.Axis("creative_angle_diversity",
                    "Creatives span distinct RE angles (ROI, location, amenities, lifestyle), not one repeated", 0.20),
            new CriticRubric.Axis("compliance_safety",
                    "HOUSING-safe: no restricted age/gender/ZIP narrowing, disclaimer present", 0.20),
            new CriticRubric.Axis("offer_clarity",
                    "A concrete, credible RE offer/hook (pre-launch price, assured ROI, possession date)", 0.11),
            new CriticRubric.Axis("local_relevance",
                    "Copy/geo reflect the local micro-market and buyer intent (end-user vs investor vs NRI)", 0.12)),
            0.72);

    // ── Targeting seeds (A3/J3/J4): interest seeds (Meta) + keyword seeds (Google). The keyword seeds
    // port the legacy _REAL_ESTATE_KEYWORDS list plus RE intent modifiers.
    private static final TargetingSeeds SEEDS = new TargetingSeeds(
            List.of(
                    "real_estate_investing", "property", "home_ownership", "first_time_home_buyer",
                    "luxury_real_estate", "apartment_living", "mortgage_loans", "interior_design",
                    "real_estate", "vacation_home_rentals", "non_resident_indian"),
            List.of(
                    // legacy _REAL_ESTATE_KEYWORDS (campaign_data.py) — the vertical-detection seed set
                    "real estate", "realty", "villa", "apartment", "residential", "property",
                    "housing", "homes", "realtor", "township", "builder", "developer",
                    // RE search-intent modifiers
                    "flats for sale", "apartments for sale", "2 bhk", "3 bhk", "ready to move",
                    "new launch", "under construction", "premium apartments", "gated community",
                    "book site visit"));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<Slot> requiredSlots(CampaignType type) {

        // Every RE plan needs identity, objective, budget, schedule, a geo catchment, and creatives.
        Set<Slot> slots = EnumSet.of(Slot.NAME, Slot.PRODUCT, Slot.OBJECTIVE, Slot.BUDGET,
                Slot.SCHEDULE, Slot.GEO, Slot.CREATIVES);

        if (type == null) {
            slots.add(Slot.AD_GROUPS);
            return Collections.unmodifiableSet(slots);
        }

        switch (type) {
            case SEARCH, DSA -> {
                // Intent-driven: keywords in ad groups, sending to a landing page.
                slots.add(Slot.AD_GROUPS);
                slots.add(Slot.KEYWORDS);
                slots.add(Slot.LANDING_PAGE);
            }
            case LEADS -> {
                // RE lead gen: an on-platform lead form under HOUSING rather than keywords.
                slots.add(Slot.AD_GROUPS);
                slots.add(Slot.LEAD_FORM);
            }
            case PMAX, ADVANTAGE_PLUS -> {
                // Goal-based: asset groups + audience signals, no keywords.
                slots.add(Slot.ASSET_GROUPS);
                slots.add(Slot.AUDIENCE);
            }
            case SHOPPING -> {
                slots.add(Slot.ASSET_GROUPS);
            }
            case SALES -> {
                slots.add(Slot.AD_GROUPS);
                slots.add(Slot.AUDIENCE);
                slots.add(Slot.LANDING_PAGE);
            }
            case DISPLAY, VIDEO, DEMAND_GEN, AWARENESS, ENGAGEMENT, TRAFFIC, APP -> {
                slots.add(Slot.AD_GROUPS);
                slots.add(Slot.AUDIENCE);
            }
            default -> slots.add(Slot.AD_GROUPS);
        }

        return Collections.unmodifiableSet(slots);
    }

    @Override
    public PolicyDefaults defaults(CampaignType type) {

        PlatformObjective objective = objectiveFor(type);
        PolicyDefaults.BudgetMode budgetMode = usesAssetGroups(type)
                ? PolicyDefaults.BudgetMode.CAMPAIGN   // PMax/Advantage+ optimise at campaign level
                : PolicyDefaults.BudgetMode.AD_SET;    // RE ad-set/ad-group level budget control

        // RE deals are considered/high-ticket: a longer click window than the generic default.
        String attributionWindow = (type == CampaignType.SEARCH || type == CampaignType.DSA)
                ? "30d_click"
                : "7d_click_1d_view";

        String bidding = objective == PlatformObjective.AWARENESS
                ? "MAXIMIZE_REACH"
                : "MAXIMIZE_CONVERSIONS";

        return new PolicyDefaults(attributionWindow, budgetMode, bidding, objective);
    }

    @Override
    public List<ComplianceRule> complianceRules(Platform p, CampaignType type) {
        // Real estate is a HOUSING special-ad category on both platforms, for every campaign type.
        return List.of(HOUSING_RULE);
    }

    @Override
    public AttributeTaxonomy attributeTaxonomy() {
        return TAXONOMY;
    }

    @Override
    public CriticRubric criticRubric() {
        return RUBRIC;
    }

    @Override
    public List<String> milestoneKeys() {
        // The RE funnel vocabulary MilestoneMapping maps live leadzump stages onto.
        return List.of("lead", "qualified", "site_visit", "booking");
    }

    @Override
    public TargetingSeeds seeds() {
        return SEEDS;
    }

    // ── J19 best-working-proxy weights. Real estate is high-ticket and considered: a competitor ad that
    // keeps running for months is a stronger "this works" tell than in an impulse-buy vertical, and a
    // theme several local builders all run corroborates the micro-market. So RE leans harder on
    // longevity + breadth and slightly less on raw iteration than the neutral defaults. These are still
    // belief-revealed proxies, not performance (J19 §5.2/§5.4).
    private static final ProxyWeights PROXY_WEIGHTS = new ProxyWeights(0.46, 0.18, 0.14, 0.08, 0.14);

    @Override
    public Optional<ProxyWeights> competitionProxyWeights() {
        return Optional.of(PROXY_WEIGHTS);
    }

    private static boolean usesAssetGroups(CampaignType type) {
        return type == CampaignType.PMAX || type == CampaignType.ADVANTAGE_PLUS;
    }

    private static PlatformObjective objectiveFor(CampaignType type) {
        if (type == null)
            return PlatformObjective.LEADS;
        return switch (type) {
            case SEARCH, DSA, LEADS, ADVANTAGE_PLUS -> PlatformObjective.LEADS; // RE is lead-driven
            case SALES, SHOPPING, PMAX -> PlatformObjective.SALES;
            case TRAFFIC -> PlatformObjective.TRAFFIC;
            case AWARENESS, DISPLAY, VIDEO -> PlatformObjective.AWARENESS;
            case ENGAGEMENT -> PlatformObjective.ENGAGEMENT;
            case DEMAND_GEN -> PlatformObjective.CONVERSIONS;
            case APP -> PlatformObjective.APP;
        };
    }

    private static AttributeTaxonomy buildTaxonomy() {
        Map<String, List<String>> axes = new LinkedHashMap<>();
        axes.put("angle", List.of(
                "price_emi", "location", "amenities", "investment_roi", "lifestyle",
                "ready_to_move", "scarcity", "trust_rera"));
        axes.put("scene", List.of(
                "interior_render", "exterior_facade", "amenity_pool", "clubhouse", "aerial_view",
                "sample_flat", "location_map", "family_lifestyle"));
        axes.put("offer", List.of(
                "pre_launch_price", "limited_units", "assured_roi", "no_emi_till_possession",
                "free_registration", "spot_booking_discount"));
        axes.put("cta", List.of(
                "book_now", "book_site_visit", "enquire_now", "download_brochure", "call_now",
                "get_quote"));
        axes.put("audiencePairing", List.of(
                "end_users", "first_time_buyers", "upgraders", "investors", "nri_investors"));
        axes.put("copyStyle", List.of(
                "number_led", "urgency", "trust", "aspirational"));
        return new AttributeTaxonomy(axes);
    }
}
