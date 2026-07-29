package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;

class PlatformRulesTest {

    private static List<ValidationIssue> check(CampaignPlan plan) {
        return PlatformRules.check(plan, ValidationContext.of(plan));
    }

    @Test
    void validPlansPassPlatformRules() {
        assertTrue(check(Plans.validSearch()).isEmpty());
        assertTrue(check(Plans.validPmax()).isEmpty());
        assertTrue(check(Plans.validMetaLeads()).isEmpty());
    }

    @Test
    void unsupportedTypeForPlatformIsRejected() {
        // SEARCH is a Google type; it is not supported on Meta.
        CampaignPlan plan = Plans.base(Map.of(Platform.META, CampaignType.SEARCH)).setBody(Plans.baseBody());

        ValidationIssue issue = Plans.find(check(plan), PlatformRules.PLATFORM_TYPE_UNSUPPORTED);
        assertEquals("/campaignTypes/META", issue.getPath());
    }

    @Test
    void googleRsaWithTooFewHeadlinesFails() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getAdGroups().get(0).getAds().get(0).setHeadlines(List.of("only-one", "two"));

        ValidationIssue issue = Plans.find(check(plan), PlatformRules.PLATFORM_RSA_HEADLINES);
        assertEquals("/body/adGroups/0/ads/0/headlines", issue.getPath());
    }

    @Test
    void googleRsaWithTooFewDescriptionsFails() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getAdGroups().get(0).getAds().get(0).setDescriptions(List.of("only-one"));

        assertTrue(Plans.has(check(plan), PlatformRules.PLATFORM_RSA_DESCRIPTIONS));
    }

    @Test
    void pmaxWithNoAssetGroupForThePlatformFails() {
        // The plan is Google PMax but the only asset group targets Meta.
        CampaignPlan plan = Plans.validPmax();
        plan.getBody().setAssetGroups(List.of(Plans.pmaxAssetGroup(Platform.META)));

        assertTrue(Plans.has(check(plan), PlatformRules.PLATFORM_PMAX_NO_ASSET_GROUP));
    }

    @Test
    void pmaxAssetGroupBelowMinimumsFails() {
        CampaignPlan plan = Plans.validPmax();
        plan.getBody().getAssetGroups().get(0).setHeadlines(List.of("only-two", "headlines"));

        ValidationIssue issue = Plans.find(check(plan), PlatformRules.PLATFORM_PMAX_ASSET_MINIMUMS);
        assertEquals("/body/assetGroups/0/headlines", issue.getPath());
    }

    @Test
    void metaLeadGenForRealEstateRequiresHousingCategory() {
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");
        // No compliance set -> housing not declared.

        ValidationIssue issue = Plans.find(check(plan), PlatformRules.PLATFORM_META_HOUSING_REQUIRED);
        assertEquals("/body/compliance/specialAdCategory", issue.getPath());
    }

    @Test
    void metaLeadGenForRealEstateWithHousingDeclaredPasses() {
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");
        plan.getBody().setCompliance(Plans.housing());

        assertFalse(Plans.has(check(plan), PlatformRules.PLATFORM_META_HOUSING_REQUIRED));
    }

    @Test
    void metaLeadGenForNonRealEstateDoesNotRequireHousing() {
        CampaignPlan plan = Plans.validMetaLeads().setVertical("ecommerce");
        assertFalse(Plans.has(check(plan), PlatformRules.PLATFORM_META_HOUSING_REQUIRED));
    }
}
