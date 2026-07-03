package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.model.CampaignPlan;

class StructuralRulesTest {

    private static List<ValidationIssue> check(CampaignPlan plan) {
        return StructuralRules.check(plan, ValidationContext.of(plan));
    }

    @Test
    void validSearchPlanHasNoStructuralIssues() {
        assertTrue(check(Plans.validSearch()).isEmpty());
    }

    @Test
    void validPmaxPlanHasNoStructuralIssues() {
        assertTrue(check(Plans.validPmax()).isEmpty());
    }

    @Test
    void searchWithoutKeywordsFailsWithPathAtAdGroups() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getAdGroups().get(0).getTargeting().setKeywords(List.of());

        List<ValidationIssue> issues = check(plan);

        assertTrue(Plans.has(issues, StructuralRules.STRUCT_MISSING));
        ValidationIssue kw = issues.stream()
                .filter(i -> "keywords".equals(i.getField())).findFirst().orElseThrow();
        assertEquals("/body/adGroups", kw.getPath());
    }

    @Test
    void searchWithoutAdGroupsFlagsAdGroupsAndKeywords() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().setAdGroups(null);

        List<String> fields = check(plan).stream().map(ValidationIssue::getField).toList();

        assertTrue(fields.contains("adGroups"));
        assertTrue(fields.contains("keywords"));
    }

    @Test
    void pmaxWithoutAssetGroupsFailsAtAssetGroups() {
        CampaignPlan plan = Plans.validPmax();
        plan.getBody().setAssetGroups(null);

        ValidationIssue issue = Plans.find(check(plan), StructuralRules.STRUCT_MISSING);
        assertEquals("/body/assetGroups", issue.getPath());
    }

    // The Search-vs-PMax matrix: each type requires a DIFFERENT structure.
    @Test
    void searchDoesNotRequireAssetGroupsAndPmaxDoesNotRequireKeywords() {
        List<String> searchFields = check(Plans.validSearch()).stream().map(ValidationIssue::getField).toList();
        List<String> pmaxFields = check(Plans.validPmax()).stream().map(ValidationIssue::getField).toList();

        // Both valid → no missing slots at all.
        assertTrue(searchFields.isEmpty());
        assertTrue(pmaxFields.isEmpty());

        // A PMax plan with only asset groups must NOT be told it needs keywords/adGroups...
        CampaignPlan pmax = Plans.validPmax();
        assertFalse(check(pmax).stream().anyMatch(i -> "keywords".equals(i.getField())));

        // ...and a Search plan with only ad groups must NOT be told it needs asset groups.
        CampaignPlan search = Plans.validSearch();
        assertFalse(check(search).stream().anyMatch(i -> "assetGroups".equals(i.getField())));
    }

    @Test
    void missingNameFailsAtRootNamePath() {
        CampaignPlan plan = Plans.validSearch().setName("  ");

        ValidationIssue name = check(plan).stream()
                .filter(i -> "name".equals(i.getField())).findFirst().orElseThrow();
        assertEquals("/name", name.getPath());
    }

    @Test
    void noCampaignTypesEmitsDedicatedCode() {
        CampaignPlan plan = Plans.validSearch().setCampaignTypes(null);
        assertTrue(Plans.has(check(plan), StructuralRules.STRUCT_NO_CAMPAIGN_TYPE));
    }
}
