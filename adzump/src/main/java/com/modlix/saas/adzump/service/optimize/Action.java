package com.modlix.saas.adzump.service.optimize;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;

/**
 * One proposed change in an {@link ActionSet} (J12 §5.1): a typed {@link #type}, targeted at an
 * ad-grain {@link #target}, carrying its typed {@link #change} payload, the {@link #rationale}, its
 * expected {@link #expectedDelta} on the blended objective (in objective points), a {@link #confidence},
 * the {@link SignificanceVerdict} it cleared, its {@link Risk}, and {@link #requiresApproval}.
 *
 * <p><b>Recommend-mode (P3):</b> {@link #requiresApproval} is <b>always true</b> — J12 only proposes;
 * autonomy routing + the actual mutation are J13 (P4). The {@link #type} reuses the persisted
 * {@link AdzumpActionAuditActionType} vocabulary so a proposal maps 1:1 onto J13's audit row.
 */
public record Action(
        AdzumpActionAuditActionType type,
        AdGrainId target,
        ActionChange change,
        String rationale,
        double expectedDelta,
        double confidence,
        SignificanceVerdict significance,
        Risk risk,
        boolean requiresApproval) {
}
