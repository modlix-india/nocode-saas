package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.dto.Severity;
import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.Money;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    private ValidationResult validate(CampaignPlan plan) {
        return this.validator.validate(plan, ValidationContext.of(plan));
    }

    private PlanCompleteness completeness(CampaignPlan plan) {
        return this.validator.completeness(plan, ValidationContext.of(plan));
    }

    @Test
    void validPlansOfEveryShapePass() {
        assertTrue(validate(Plans.validSearch()).isValid());
        assertTrue(validate(Plans.validPmax()).isValid());
        assertTrue(validate(Plans.validMetaLeads()).isValid());
    }

    // The load-bearing invariant (spec §8 property test): valid iff there are no ERROR issues.
    @Test
    void validEqualsNoErrorIssues() {
        for (CampaignPlan plan : List.of(Plans.validSearch(), Plans.validPmax(),
                Plans.validMetaLeads().setVertical("real_estate"), // trips errors
                new CampaignPlan())) {
            ValidationResult r = validate(plan);
            boolean noErrors = r.getIssues().stream().noneMatch(i -> i.getSeverity() == Severity.ERROR);
            assertEquals(noErrors, r.isValid());
        }
    }

    @Test
    void warningsDoNotBlock() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setDailyBudget(new Money(BigDecimal.ZERO, "USD")); // thin -> WARNING

        ValidationResult r = validate(plan);

        assertTrue(r.isValid());
        assertTrue(Plans.has(r.getIssues(), BusinessRules.BUSINESS_THIN_BUDGET));
    }

    @Test
    void aStructurallyBrokenPlanIsInvalid() {
        ValidationResult r = validate(new CampaignPlan());
        assertFalse(r.isValid());
        assertTrue(Plans.has(r.getIssues(), StructuralRules.STRUCT_NO_CAMPAIGN_TYPE));
    }

    @Test
    void layersConcatenateAndArePathAddressed() {
        // A real-estate Meta lead plan with no HOUSING category trips both a platform and a vertical rule.
        CampaignPlan plan = Plans.validMetaLeads().setVertical("real_estate");
        List<ValidationIssue> issues = validate(plan).getIssues();

        assertTrue(Plans.has(issues, PlatformRules.PLATFORM_META_HOUSING_REQUIRED));
        assertTrue(Plans.has(issues, VerticalRules.VERTICAL_SPECIAL_CATEGORY_MISSING));
        assertTrue(issues.stream().allMatch(i -> i.getPath() != null && i.getPath().startsWith("/")));
    }

    // ---- completeness ----

    @Test
    void completenessOfAValidSearchPlanIsComplete() {
        PlanCompleteness c = completeness(Plans.validSearch());

        assertTrue(c.isComplete());
        assertTrue(c.getMissingRequired().isEmpty());
        assertTrue(c.getSlots().containsKey(Slots.KEYWORDS));
        assertFalse(c.getSlots().containsKey(Slots.ASSET_GROUPS)); // Search does not require asset groups
    }

    @Test
    void completenessListsTheMissingSlotByName() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getAdGroups().get(0).getTargeting().setKeywords(List.of());

        PlanCompleteness c = completeness(plan);

        assertFalse(c.isComplete());
        assertTrue(c.getMissingRequired().contains(Slots.KEYWORDS));
    }

    // The Search-vs-PMax structural difference, seen through completeness.
    @Test
    void completenessIsTypeAware() {
        PlanCompleteness pmax = completeness(Plans.validPmax());

        assertTrue(pmax.getSlots().containsKey(Slots.ASSET_GROUPS));
        assertFalse(pmax.getSlots().containsKey(Slots.KEYWORDS));
        assertTrue(pmax.isComplete());
    }
}
