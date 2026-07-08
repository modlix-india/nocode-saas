package com.modlix.saas.adzump.service.apply;

import java.util.List;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.service.optimize.ActionSet;

/**
 * The routed form of an {@link ActionSet} (J13 §5.1): every proposed {@link com.modlix.saas.adzump.service.optimize.Action}
 * tagged with its {@link ApplyRoute} decision, carried alongside the {@code snapshotId} the diagnosis was
 * read from (so the {@link ActionApplier} can link the audit to the basis snapshot and the staleness
 * guardrail can judge the snapshot's age). Produced by {@link AutonomyRouter#route} and consumed by
 * {@link ActionApplier#apply}.
 *
 * @param campaignPlanId the plan these actions target.
 * @param snapshotId     the snapshot the ActionSet was diagnosed from ({@code null} when unknown).
 * @param routed         each action + its routing decision, in the ActionSet's ranked order.
 */
public record ApplyPlan(ULong campaignPlanId, ULong snapshotId, List<RoutedAction> routed) {

    /** How many actions the router marked {@link ApplyRoute#APPLY}. */
    public long applyCount() {
        return this.routed.stream().filter(r -> r.route() == ApplyRoute.APPLY).count();
    }
}
