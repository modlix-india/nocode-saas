package com.modlix.saas.adzump.service.optimize;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;

/**
 * A candidate action the {@link SignificanceGate} suppressed (J12 §5.3), carried on the
 * {@link ActionSet} so a "no action" is <b>explainable, not silent</b>: the rail and the audit can
 * show <i>which</i> change was considered, at <i>which</i> grain, and <i>why</i> it did not fire
 * (the {@link SignificanceVerdict#outcome()} + {@link SignificanceVerdict#detail()}).
 */
public record SuppressedCandidate(
        AdzumpActionAuditActionType type,
        AdGrainId target,
        SignificanceVerdict verdict,
        String rationale) {
}
