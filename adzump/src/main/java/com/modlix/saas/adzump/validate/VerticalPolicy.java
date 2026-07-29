package com.modlix.saas.adzump.validate;

import com.modlix.saas.adzump.enums.SpecialAdCategory;

/**
 * J6 fallback for the slice of vertical policy the platform + vertical rule layers need: does a vertical
 * require a special ad category (real estate &rarr; {@code HOUSING}). Shared by {@link PlatformRules}
 * (Meta lead-gen HOUSING) and {@link VerticalRules} (plan-wide special-category enforcement) so the two
 * layers agree on what "the vertical requires it" means.
 *
 * <p>TODO(J5): replace with {@code VerticalRegistry.get(vertical).complianceRules(platform, type)} — the
 * required special category is vertical knowledge, not a literal that should live here. Real estate is
 * the only tuned vertical in this slice; every other vertical returns "no special category required".
 */
final class VerticalPolicy {

    private VerticalPolicy() {
    }

    /** The special ad category a vertical mandates, or {@code null} if none. */
    static SpecialAdCategory requiredSpecialCategory(String vertical) {
        return requiresHousing(vertical) ? SpecialAdCategory.HOUSING : null;
    }

    /** Whether the vertical is real-estate-like and therefore requires the HOUSING special ad category. */
    static boolean requiresHousing(String vertical) {
        if (vertical == null)
            return false;
        String v = vertical.toLowerCase().replaceAll("[^a-z]", "");
        return v.contains("realestate") || v.contains("realty") || v.contains("housing")
                || v.contains("property");
    }
}
