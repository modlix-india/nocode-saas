package com.modlix.saas.adzump.vertical;

import java.util.List;

import com.modlix.saas.adzump.enums.SpecialAdCategory;

/**
 * A vertical/platform compliance rule consumed by J6 (validate) and J3/J4 (compile). The canonical
 * example is Meta's {@link SpecialAdCategory#HOUSING} special-ad-category for real-estate lead gen,
 * which locks age/gender/ZIP/detailed targeting and requires a disclaimer.
 *
 * @param category            the special-ad-category this rule declares (e.g. HOUSING).
 * @param description         human-readable rationale, surfaced in the validation panel.
 * @param restrictedTargeting the targeting axes locked / ignored under this category, by plan-field
 *                            name (e.g. {@code "demographics.ageMin"}, {@code "demographics.genders"},
 *                            {@code "geo.zip"}, {@code "audiences.interests"}); J6 flags any narrowing
 *                            of these, J7 drops them from the payload.
 * @param disclaimerRequired  whether a compliance disclaimer must be present on the plan.
 */
public record ComplianceRule(
        SpecialAdCategory category,
        String description,
        List<String> restrictedTargeting,
        boolean disclaimerRequired) {

    public ComplianceRule {
        restrictedTargeting = restrictedTargeting == null ? List.of() : List.copyOf(restrictedTargeting);
    }
}
