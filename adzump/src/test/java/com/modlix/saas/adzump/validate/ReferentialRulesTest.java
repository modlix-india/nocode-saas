package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Audiences;
import com.modlix.saas.adzump.model.CampaignPlan;

class ReferentialRulesTest {

    @Test
    void permissiveContextSkipsFetchedIdMembership() {
        CampaignPlan plan = Plans.validMetaLeads();
        plan.getBody().getAdGroups().get(0).getTargeting()
                .setAudiences(new Audiences().setCustomAudienceIds(List.of("aud-never-fetched")));

        // Empty fetched-id set -> permissive -> no REF_UNKNOWN_ID.
        List<ValidationIssue> issues = ReferentialRules.check(plan, ValidationContext.of(plan));
        assertFalse(Plans.has(issues, ReferentialRules.REF_UNKNOWN_ID));
    }

    @Test
    void unknownAudienceIdFailsWhenFetchedSetIsPresent() {
        CampaignPlan plan = Plans.validMetaLeads();
        plan.getBody().getAdGroups().get(0).getTargeting()
                .setAudiences(new Audiences().setCustomAudienceIds(List.of("aud-1", "aud-invented")));

        // A non-empty set makes the check active; "aud-invented" is not in it.
        ValidationContext ctx = ValidationContext.of(plan, Set.of("aud-1"));
        List<ValidationIssue> issues = ReferentialRules.check(plan, ctx);

        ValidationIssue issue = Plans.find(issues, ReferentialRules.REF_UNKNOWN_ID);
        assertEquals("/body/adGroups/0/targeting/audiences/customAudienceIds/1", issue.getPath());
    }

    @Test
    void fetchedIdThatIsPresentPasses() {
        CampaignPlan plan = Plans.validMetaLeads();
        plan.getBody().getAdGroups().get(0).getTargeting()
                .setAudiences(new Audiences().setCustomAudienceIds(List.of("aud-1")));

        ValidationContext ctx = ValidationContext.of(plan, Set.of("aud-1"));
        assertFalse(Plans.has(ReferentialRules.check(plan, ctx), ReferentialRules.REF_UNKNOWN_ID));
    }

    @Test
    void adGroupTargetingUntargetedPlatformIsInconsistent() {
        // Plan campaigns only on META, but an ad group targets GOOGLE.
        CampaignPlan plan = Plans.validMetaLeads();
        plan.getBody().getAdGroups().get(0).setPlatform(Platform.GOOGLE);

        List<ValidationIssue> issues = ReferentialRules.check(plan, ValidationContext.of(plan));

        ValidationIssue issue = Plans.find(issues, ReferentialRules.REF_PLATFORM_MISMATCH);
        assertEquals("/body/adGroups/0/platform", issue.getPath());
    }

    @Test
    void adReferencingAForeignLeadFormFails() {
        CampaignPlan plan = Plans.validMetaLeads();
        plan.getBody().getAdGroups().get(0).getAds().get(0).setLeadFormId("some-other-form");

        List<ValidationIssue> issues = ReferentialRules.check(plan, ValidationContext.of(plan));

        ValidationIssue issue = Plans.find(issues, ReferentialRules.REF_LEADFORM_UNKNOWN);
        assertEquals("/body/adGroups/0/ads/0/leadFormId", issue.getPath());
    }

    @Test
    void consistentPlanHasNoReferentialIssues() {
        CampaignPlan plan = Plans.validMetaLeads();
        assertTrue(ReferentialRules.check(plan, ValidationContext.of(plan)).isEmpty());
    }

    @Test
    void nullBodyIsHandled() {
        CampaignPlan plan = Plans.base(java.util.Map.of(Platform.GOOGLE, CampaignType.SEARCH));
        assertTrue(ReferentialRules.check(plan, ValidationContext.of(plan)).isEmpty());
    }
}
