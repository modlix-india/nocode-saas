package com.modlix.saas.adzump.service.apply;

import java.util.List;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;

/**
 * The outcome of one {@link ActionApplier#apply} run (J13 §5.3): every action's {@link ApplyDecision}
 * plus the id of the fresh post-apply snapshot ({@code null} when nothing applied, so no re-measure was
 * triggered). The decisions mirror, in order, the {@link ApplyPlan}'s routed actions.
 *
 * @param campaignPlanId the plan applied against.
 * @param decisions      per-action recorded outcomes (verdict + audit row id).
 * @param newSnapshotId  the re-measure snapshot linked onto the applied rows, or {@code null}.
 */
public record ApplyResult(ULong campaignPlanId, List<ApplyDecision> decisions, ULong newSnapshotId) {

    /** How many actions ended in the {@code APPLIED} verdict. */
    public long appliedCount() {
        return this.decisions.stream().filter(d -> d.verdict() == AdzumpActionAuditVerdict.APPLIED).count();
    }
}
