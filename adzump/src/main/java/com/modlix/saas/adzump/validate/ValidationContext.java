package com.modlix.saas.adzump.validate;

import java.util.Map;
import java.util.Set;

import com.modlix.saas.adzump.model.CampaignPlan;

/**
 * Immutable inputs the pure rule layers validate a {@link CampaignPlan} against. Everything a layer
 * needs that is NOT on the plan itself lives here, so {@code (plan, ctx) -> issues} stays a pure,
 * deterministic, unit-testable function with no I/O.
 *
 * <ul>
 *   <li>{@code fetchedIds} — the session's fetched platform-id set (accounts, pages, pixels, audiences,
 *       geo, keyword, MCC ids the agent legitimately discovered). The referential layer rejects any
 *       plan id not in this set. <b>P0/P1 stub:</b> the service passes an empty set until A1 wires the
 *       session fetched-id registry; an empty set makes the referential membership check permissive
 *       (see {@link #referentialPermissive()}) — TODO(A1 gate).</li>
 *   <li>{@code vertical} — the plan's deduced vertical (e.g. {@code "real_estate"}); drives the vertical
 *       compliance layer. TODO(J5): resolve the required-slot set / compliance rules through
 *       {@code VerticalRegistry.get(vertical)} instead of J6's built-in fallbacks.</li>
 *   <li>{@code effectiveConfig} — resolved effective config bits (campaign-override → account-default →
 *       vertical-default) for business checks that need caps. P0 passes an empty map.</li>
 * </ul>
 */
public record ValidationContext(Set<String> fetchedIds, String vertical, Map<String, Object> effectiveConfig) {

    public ValidationContext {
        fetchedIds = fetchedIds == null ? Set.of() : Set.copyOf(fetchedIds);
        effectiveConfig = effectiveConfig == null ? Map.of() : Map.copyOf(effectiveConfig);
    }

    /** Vertical from the plan, empty fetched-id set (P0 referential-permissive), empty config. */
    public static ValidationContext of(CampaignPlan plan) {
        return new ValidationContext(Set.of(), plan == null ? null : plan.getVertical(), Map.of());
    }

    /** Vertical from the plan plus an explicit session fetched-id set (enables the referential check). */
    public static ValidationContext of(CampaignPlan plan, Set<String> fetchedIds) {
        return new ValidationContext(fetchedIds, plan == null ? null : plan.getVertical(), Map.of());
    }

    /**
     * When no session fetched-id set is wired (empty), the referential <i>membership</i> check is
     * skipped (permissive) — TODO(A1 gate). Internal-consistency referential checks still run.
     */
    public boolean referentialPermissive() {
        return this.fetchedIds.isEmpty();
    }
}
