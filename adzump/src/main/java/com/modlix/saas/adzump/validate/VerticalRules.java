package com.modlix.saas.adzump.validate;

import static com.modlix.saas.adzump.validate.ValidationSupport.notEmpty;

import java.util.ArrayList;
import java.util.List;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Demographics;
import com.modlix.saas.adzump.model.Geo;
import com.modlix.saas.adzump.model.Targeting;

/**
 * Layer 4 — <b>vertical compliance</b>. For a vertical that mandates a special ad category (real estate
 * &rarr; HOUSING): the plan must declare that category (plan-wide, both platforms); a declared
 * special-category plan may not use the restricted targeting the category forbids (age / gender
 * narrowing, sub-radius geofencing — Meta's housing/employment/credit restrictions); and the vertical's
 * required lead-capture slot must be present.
 *
 * <p>All findings are {@code ERROR}. This is the plan-wide vertical enforcement; the Meta-lead-gen
 * specific HOUSING check lives in {@link PlatformRules} (a HOUSING RE Meta-LEADS plan legitimately trips
 * both, at different paths). TODO(J5): drive off {@code VerticalRegistry.get(vertical)} rather than the
 * {@link VerticalPolicy} fallback.
 */
public final class VerticalRules {

    public static final String VERTICAL_SPECIAL_CATEGORY_MISSING = "VERTICAL_SPECIAL_CATEGORY_MISSING";
    public static final String VERTICAL_RESTRICTED_TARGETING = "VERTICAL_RESTRICTED_TARGETING";
    public static final String VERTICAL_SLOT_MISSING = "VERTICAL_SLOT_MISSING";

    private static final int HOUSING_MIN_AGE = 18;
    private static final int HOUSING_MAX_AGE = 65;
    private static final double HOUSING_MIN_RADIUS_KM = 24.0; // Meta housing floor (~15 miles)

    private VerticalRules() {
    }

    public static List<ValidationIssue> check(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();

        SpecialAdCategory required = VerticalPolicy.requiredSpecialCategory(ctx.vertical());
        if (required == null)
            return issues; // vertical mandates no special category in this slice

        CampaignPlanBody body = plan == null ? null : plan.getBody();
        SpecialAdCategory declared = declared(body);

        if (declared != required)
            issues.add(ValidationIssue.error(VERTICAL_SPECIAL_CATEGORY_MISSING,
                    "/body/compliance/specialAdCategory", "specialAdCategory",
                    "vertical '" + ctx.vertical() + "' requires the " + required + " special ad category"));
        else
            checkRestrictedTargeting(issues, body);

        // Vertical-required slot: a lead-capture path (lead form or an instrumented landing page).
        boolean leadCapture = body != null && (body.getLeadForm() != null || body.getLandingPage() != null);
        if (!leadCapture)
            issues.add(ValidationIssue.error(VERTICAL_SLOT_MISSING, "/body", "leadCapture",
                    "vertical '" + ctx.vertical() + "' requires a lead-capture path (leadForm or landingPage)"));

        return issues;
    }

    private static void checkRestrictedTargeting(List<ValidationIssue> issues, CampaignPlanBody body) {

        if (body == null || body.getAdGroups() == null)
            return;

        List<AdGroup> adGroups = body.getAdGroups();
        for (int i = 0; i < adGroups.size(); i++) {
            AdGroup ag = adGroups.get(i);
            if (ag == null || ag.getTargeting() == null)
                continue;
            Targeting t = ag.getTargeting();
            String base = "/body/adGroups/" + i + "/targeting";

            Demographics d = t.getDemographics();
            if (d != null) {
                if (d.getAgeMin() != null && d.getAgeMin() > HOUSING_MIN_AGE)
                    issues.add(restricted(base + "/demographics/ageMin", "ageMin",
                            "special-category ads may not narrow the minimum age"));
                if (d.getAgeMax() != null && d.getAgeMax() < HOUSING_MAX_AGE)
                    issues.add(restricted(base + "/demographics/ageMax", "ageMax",
                            "special-category ads may not narrow the maximum age"));
                if (notEmpty(d.getGenders()))
                    issues.add(restricted(base + "/demographics/genders", "genders",
                            "special-category ads may not target by gender"));
            }

            Geo g = t.getGeo();
            if (g != null && g.getRadiusKm() != null && g.getRadiusKm() < HOUSING_MIN_RADIUS_KM)
                issues.add(restricted(base + "/geo/radiusKm", "radiusKm",
                        "special-category ads require a radius of at least " + HOUSING_MIN_RADIUS_KM + " km"));
        }
    }

    private static ValidationIssue restricted(String path, String field, String message) {
        return ValidationIssue.error(VERTICAL_RESTRICTED_TARGETING, path, field, message);
    }

    private static SpecialAdCategory declared(CampaignPlanBody body) {
        Compliance c = body == null ? null : body.getCompliance();
        return c == null ? null : c.getSpecialAdCategory();
    }
}
