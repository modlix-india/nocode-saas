package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.Demographics;
import com.modlix.saas.adzump.model.Geo;

class VerticalRulesTest {

    private static List<ValidationIssue> check(CampaignPlan plan) {
        return VerticalRules.check(plan, ValidationContext.of(plan));
    }

    @Test
    void nonRegulatedVerticalHasNoVerticalIssues() {
        assertTrue(check(Plans.validMetaLeads().setVertical("ecommerce")).isEmpty());
        assertTrue(check(Plans.validMetaLeads().setVertical(null)).isEmpty());
    }

    @Test
    void realEstateWithoutSpecialCategoryFails() {
        // Has a lead form (lead-capture present) so ONLY the special-category rule trips.
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");

        ValidationIssue issue = Plans.find(check(plan), VerticalRules.VERTICAL_SPECIAL_CATEGORY_MISSING);
        assertEquals("/body/compliance/specialAdCategory", issue.getPath());
        assertFalse(Plans.has(check(plan), VerticalRules.VERTICAL_SLOT_MISSING));
    }

    @Test
    void realEstateWithHousingAndSaneTargetingPasses() {
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");
        plan.getBody().setCompliance(Plans.housing());

        assertTrue(check(plan).isEmpty());
    }

    @Test
    void housingPlanTargetingByGenderIsRestricted() {
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");
        plan.getBody().setCompliance(Plans.housing());
        plan.getBody().getAdGroups().get(0).getTargeting()
                .setDemographics(new Demographics().setGenders(List.of("male")));

        ValidationIssue issue = Plans.find(check(plan), VerticalRules.VERTICAL_RESTRICTED_TARGETING);
        assertEquals("/body/adGroups/0/targeting/demographics/genders", issue.getPath());
    }

    @Test
    void housingPlanWithSubMinimumRadiusIsRestricted() {
        CampaignPlan plan = Plans.validSearch().setVertical("real_estate");
        plan.getBody().setCompliance(Plans.housing());
        plan.getBody().setLandingPage(new com.modlix.saas.adzump.model.LandingPageRef().setUrl("https://x")); // lead-capture
        plan.getBody().getAdGroups().get(0).getTargeting().setGeo(new Geo().setRadiusKm(5.0));

        ValidationIssue issue = Plans.find(check(plan), VerticalRules.VERTICAL_RESTRICTED_TARGETING);
        assertEquals("/body/adGroups/0/targeting/geo/radiusKm", issue.getPath());
    }

    @Test
    void realEstateWithoutAnyLeadCapturePathFails() {
        // validSearch has neither leadForm nor landingPage; HOUSING declared so the category rule is quiet.
        CampaignPlan plan = Plans.validSearch().setVertical("real_estate");
        plan.getBody().setCompliance(Plans.housing());

        ValidationIssue issue = Plans.find(check(plan), VerticalRules.VERTICAL_SLOT_MISSING);
        assertEquals("/body", issue.getPath());
    }
}
