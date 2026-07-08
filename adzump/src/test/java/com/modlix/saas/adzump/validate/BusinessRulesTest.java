package com.modlix.saas.adzump.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dto.Severity;
import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.BudgetSplit;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.Money;

class BusinessRulesTest {

    private static List<ValidationIssue> check(CampaignPlan plan) {
        return BusinessRules.check(plan, ValidationContext.of(plan));
    }

    @Test
    void validPlanPassesBusinessRules() {
        assertTrue(check(Plans.validSearch()).isEmpty());
    }

    @Test
    void missingProductProfileIsAHardStop() {
        CampaignPlan plan = Plans.validSearch().setProductId(null);

        ValidationIssue issue = Plans.find(check(plan), BusinessRules.BUSINESS_NO_PRODUCT_PROFILE);
        assertEquals("/productId", issue.getPath());
        assertEquals(Severity.ERROR, issue.getSeverity());
    }

    @Test
    void bothDailyAndTotalBudgetFailsXor() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setTotalBudget(Plans.money(3000));

        assertTrue(Plans.has(check(plan), BusinessRules.BUSINESS_BUDGET_XOR));
    }

    @Test
    void neitherDailyNorTotalBudgetFailsXor() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setDailyBudget(null);

        assertTrue(Plans.has(check(plan), BusinessRules.BUSINESS_BUDGET_XOR));
    }

    @Test
    void thinBudgetIsAWarningNotAnError() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setDailyBudget(new Money(BigDecimal.ZERO, "USD"));

        ValidationIssue issue = Plans.find(check(plan), BusinessRules.BUSINESS_THIN_BUDGET);
        assertEquals(Severity.WARNING, issue.getSeverity());
    }

    @Test
    void splitNotSummingToHundredFails() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setSplit(List.of(new BudgetSplit().setPlatform(Platform.GOOGLE).setPercent(60.0)));

        ValidationIssue issue = Plans.find(check(plan), BusinessRules.BUSINESS_BUDGET_SPLIT);
        assertEquals("/body/budget/split", issue.getPath());
    }

    @Test
    void splitReferencingUntargetedPlatformFails() {
        // Plan campaigns only on Google; a 100%% Meta split sums fine but references an untargeted platform.
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getBudget().setSplit(List.of(new BudgetSplit().setPlatform(Platform.META).setPercent(100.0)));

        ValidationIssue issue = Plans.find(check(plan), BusinessRules.BUSINESS_BUDGET_SPLIT);
        assertEquals("/body/budget/split/0/platform", issue.getPath());
    }

    @Test
    void scheduleEndingBeforeStartFails() {
        CampaignPlan plan = Plans.validSearch();
        plan.getBody().getSchedule()
                .setStartAt(LocalDateTime.of(2026, 5, 1, 0, 0))
                .setEndAt(LocalDateTime.of(2026, 4, 1, 0, 0));

        ValidationIssue issue = Plans.find(check(plan), BusinessRules.BUSINESS_SCHEDULE_ORDER);
        assertEquals("/body/schedule", issue.getPath());
    }
}
