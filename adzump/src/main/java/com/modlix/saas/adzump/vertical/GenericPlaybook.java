package com.modlix.saas.adzump.vertical;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;

/**
 * The vertical-neutral fallback. Provides safe, minimal required-slots + neutral defaults + an empty
 * taxonomy so a product whose vertical A2 could not confidently deduce still builds (with broader
 * validation warnings). It carries <b>no</b> industry knowledge — no compliance rules, no attribute
 * values, no targeting seeds. Real estate is the first <i>tuned</i> playbook; this keeps the platform
 * domain-general from day one.
 *
 * <p>TODO(J5 open question §9.1): playbook content is authored as code constants for P1. A later slice
 * may load per-vertical content from a data file (YAML/JSON) with a schema check so non-engineers can
 * tune it. The {@link VerticalPlaybook} seam is stable across that change.
 */
@Component
public class GenericPlaybook implements VerticalPlaybook {

    static final String CODE = "generic";

    // A neutral, conservative critic rubric. RE overlays its own axes on top of these shared ones.
    // TODO(J5 open question §9.3): factor the shared base out once a second tuned vertical exists.
    private static final CriticRubric BASE_RUBRIC = new CriticRubric(List.of(
            new CriticRubric.Axis("targeting_coherence",
                    "Audience/geo/keyword choices are internally consistent and match the objective", 0.34),
            new CriticRubric.Axis("structure_fit",
                    "Campaign structure matches the campaign type (ad groups vs asset groups) and objective", 0.33),
            new CriticRubric.Axis("creative_quality",
                    "Copy/creative is clear, on-message, and free of obvious policy risks", 0.33)),
            0.65);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<Slot> requiredSlots(CampaignType type) {

        // Universal minimum: identity + objective + budget + schedule + at least one creative.
        Set<Slot> slots = EnumSet.of(Slot.NAME, Slot.PRODUCT, Slot.OBJECTIVE, Slot.BUDGET,
                Slot.SCHEDULE, Slot.CREATIVES);

        // The one structural fork every platform shares: PMax/Advantage+ carry asset groups; every
        // other type carries ad groups. Mirrors the P0 "adGroupsOrAssetGroups" completeness check.
        if (usesAssetGroups(type))
            slots.add(Slot.ASSET_GROUPS);
        else
            slots.add(Slot.AD_GROUPS);

        return java.util.Collections.unmodifiableSet(slots);
    }

    @Override
    public PolicyDefaults defaults(CampaignType type) {
        // Conservative, platform-agnostic neutrals. Non-null so an unknown vertical still compiles;
        // any of these may be overridden per account (J7 account-default) or per campaign.
        return new PolicyDefaults(
                "7d_click_1d_view",
                PolicyDefaults.BudgetMode.CAMPAIGN,
                "MAXIMIZE_CONVERSIONS",
                neutralObjective(type));
    }

    @Override
    public List<ComplianceRule> complianceRules(Platform p, CampaignType type) {
        // No industry compliance without a known vertical.
        return List.of();
    }

    @Override
    public AttributeTaxonomy attributeTaxonomy() {
        return AttributeTaxonomy.empty();
    }

    @Override
    public CriticRubric criticRubric() {
        return BASE_RUBRIC;
    }

    @Override
    public List<String> milestoneKeys() {
        // A generic funnel; MilestoneMapping maps live leadzump stages onto these.
        return List.of("lead", "qualified", "conversion");
    }

    @Override
    public TargetingSeeds seeds() {
        return TargetingSeeds.empty();
    }

    static boolean usesAssetGroups(CampaignType type) {
        return type == CampaignType.PMAX || type == CampaignType.ADVANTAGE_PLUS;
    }

    private static PlatformObjective neutralObjective(CampaignType type) {
        if (type == null)
            return PlatformObjective.CONVERSIONS;
        return switch (type) {
            case LEADS -> PlatformObjective.LEADS;
            case SALES, SHOPPING, PMAX -> PlatformObjective.SALES;
            case TRAFFIC, DSA -> PlatformObjective.TRAFFIC;
            case AWARENESS, DISPLAY, VIDEO -> PlatformObjective.AWARENESS;
            case ENGAGEMENT -> PlatformObjective.ENGAGEMENT;
            case APP -> PlatformObjective.APP;
            default -> PlatformObjective.CONVERSIONS;
        };
    }
}
