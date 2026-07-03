package com.modlix.saas.adzump.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.validate.PlanValidator;
import com.modlix.saas.adzump.validate.ValidationContext;

/**
 * Service seam for J6. Reads the plan (tenant-gated by {@link CampaignPlanService#read}) and runs the
 * pure {@link PlanValidator} over it. Validation is a non-mutating, read-ish check, so it carries
 * <b>no {@code @PreAuthorize}</b>; tenant scope is enforced by the by-id read, which requires the caller
 * to manage the plan's client (mirrors {@code AutonomyConfigService.putCampaignOverride}, which locates a
 * campaign by id and trusts {@code read()}'s managed-client gate).
 */
@Service
public class PlanValidationService {

    private final CampaignPlanService campaignPlanService;
    private final PlanValidator planValidator;

    public PlanValidationService(CampaignPlanService campaignPlanService, PlanValidator planValidator) {
        this.campaignPlanService = campaignPlanService;
        this.planValidator = planValidator;
    }

    /**
     * Validates the plan and returns a structured, path-addressed result. {@code valid} is true iff there
     * are no {@code ERROR} issues (WARNINGs never block).
     *
     * @param planId           the plan to validate
     * @param targetClientCode reserved for signature symmetry with the mutating family; a plan is located
     *                         by its global id and tenant-gated by {@link CampaignPlanService#read}, so no
     *                         separate effective-client resolution is needed on this read-ish path.
     */
    public ValidationResult validate(ULong planId, String targetClientCode) {

        // read() enforces PLAN_NOT_FOUND + the managed-client tenant gate on the plan row.
        CampaignPlan plan = this.campaignPlanService.read(planId);

        // ctx: vertical from the plan; empty fetched-id set makes the referential membership check
        // permissive until A1 wires the session fetched-id registry (TODO A1 gate, see ValidationContext).
        ValidationContext ctx = ValidationContext.of(plan);

        return this.planValidator.validate(plan, ctx);
    }
}
