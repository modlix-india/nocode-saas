package com.modlix.saas.adzump.service;

import java.util.ArrayList;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.dto.Severity;
import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.dto.ValidationResult;

@Service
public class PlanValidationService {

    private static final String STRUCT_MISSING = "STRUCT_MISSING";

    private final CampaignPlanService campaignPlanService;

    public PlanValidationService(CampaignPlanService campaignPlanService) {
        this.campaignPlanService = campaignPlanService;
    }

    // TODO(J6 five-layer validator): structural, platform-policy, budget/bid sanity,
    // compliance (special ad categories), and cross-entity reference layers. P0 is
    // structural-lite only: it mirrors the completeness slot checks.
    public ValidationResult validate(ULong planId) {

        // completeness() carries the not-found + tenant checks.
        PlanCompleteness completeness = this.campaignPlanService.completeness(planId);

        List<ValidationIssue> issues = new ArrayList<>();

        for (String slot : completeness.getMissingRequired())
            issues.add(new ValidationIssue()
                    .setCode(STRUCT_MISSING)
                    .setSeverity(Severity.ERROR)
                    .setField(slot)
                    .setMessage("Required plan slot '" + slot + "' is missing or empty"));

        return new ValidationResult()
                .setValid(issues.isEmpty())
                .setIssues(issues);
    }
}
