package com.modlix.saas.adzump.validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.model.CampaignPlan;

/**
 * Layer 1 — <b>structural</b> (the P0 layer). Required fields present for the plan's {@code CampaignType}
 * set, type-dispatched via {@link Slots#forPlan}: a Search plan needs {@code adGroups + keywords}; a
 * PMax / Advantage+ plan needs {@code assetGroups}. Pure: {@code (plan, ctx) -> issues}, no I/O.
 *
 * <p>All findings are {@code ERROR} (a missing required slot blocks compile/launch). Within-slot
 * platform minimums (RSA headline counts, PMax asset minimums) belong to {@link PlatformRules}.
 */
public final class StructuralRules {

    public static final String STRUCT_MISSING = "STRUCT_MISSING";
    public static final String STRUCT_NO_CAMPAIGN_TYPE = "STRUCT_NO_CAMPAIGN_TYPE";

    private StructuralRules() {
    }

    public static List<ValidationIssue> check(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();

        if (plan == null) {
            issues.add(ValidationIssue.error(STRUCT_MISSING, "/", "plan", "plan is null"));
            return issues;
        }

        if (plan.getCampaignTypes() == null || plan.getCampaignTypes().isEmpty())
            issues.add(ValidationIssue.error(STRUCT_NO_CAMPAIGN_TYPE, "/campaignTypes", "campaignTypes",
                    "at least one platform campaign type is required"));

        Set<String> required = Slots.forPlan(plan);
        for (String slot : required)
            if (!Slots.filled(slot, plan))
                issues.add(ValidationIssue.error(STRUCT_MISSING, Slots.path(slot), slot,
                        "required slot '" + slot + "' is missing or empty"));

        return issues;
    }
}
