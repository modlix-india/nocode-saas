package com.modlix.saas.adzump.validate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.dto.Severity;
import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.model.CampaignPlan;

/**
 * J6 — the deterministic gate between a {@link CampaignPlan} and any compile or launch. Orchestrates the
 * five pure rule layers ({@link StructuralRules} &rarr; {@link ReferentialRules} &rarr;
 * {@link PlatformRules} &rarr; {@link VerticalRules} &rarr; {@link BusinessRules}), concatenating their
 * issues. It is the single definition of "valid plan" so A3 (repair loop), A1 (completeness rail) and J8
 * (pre-launch gate) all agree.
 *
 * <p><b>Pure + deterministic:</b> {@code validate(plan, ctx)} and {@code completeness(plan, ctx)} do no
 * I/O, so they are unit-testable without any platform call and reproducible in the eval harness. This is
 * a {@code @Component} only so the services can inject it; it holds no state and no collaborators.
 */
@Component
public class PlanValidator {

    /**
     * Runs all five layers and returns the concatenated result. {@code valid} is true iff no issue is an
     * {@link Severity#ERROR} — {@code WARNING}s surface but never block (spec §5.1).
     */
    public ValidationResult validate(CampaignPlan plan, ValidationContext ctx) {

        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(StructuralRules.check(plan, ctx));
        issues.addAll(ReferentialRules.check(plan, ctx));
        issues.addAll(PlatformRules.check(plan, ctx));
        issues.addAll(VerticalRules.check(plan, ctx));
        issues.addAll(BusinessRules.check(plan, ctx));

        boolean valid = issues.stream().noneMatch(i -> i.getSeverity() == Severity.ERROR);

        return new ValidationResult()
                .setValid(valid)
                .setIssues(issues);
    }

    /**
     * Vertical- + type-aware completeness (feeds A1's rail): the required-slot set for the plan's
     * {@code CampaignType}s (via {@link Slots#forPlan}) split into filled vs missing. {@code complete}
     * means every required slot is present; launch-readiness additionally needs
     * {@link #validate}{@code .isValid()}, which the caller composes.
     */
    public PlanCompleteness completeness(CampaignPlan plan, ValidationContext ctx) {

        Set<String> required = Slots.forPlan(plan);

        Map<String, Boolean> slots = new LinkedHashMap<>();
        for (String slot : required)
            slots.put(slot, Slots.filled(slot, plan));

        List<String> missingRequired = slots.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .toList();

        return new PlanCompleteness()
                .setComplete(missingRequired.isEmpty())
                .setMissingRequired(missingRequired)
                .setSlots(slots);
    }
}
