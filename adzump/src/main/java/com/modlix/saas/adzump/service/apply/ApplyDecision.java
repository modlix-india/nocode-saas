package com.modlix.saas.adzump.service.apply;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;

/**
 * The recorded outcome of routing/applying one action (J13 §5.3/§5.4): the action type, the persisted
 * {@link AdzumpActionAuditVerdict} it ended in (APPLIED / QUEUED / SUPPRESSED / REJECTED / REVERSED), the
 * machine-readable reason, and the {@code adzump_action_audit} row id it was written to (so a caller can
 * later approve / reject / reverse it).
 */
public record ApplyDecision(
        AdzumpActionAuditActionType type,
        AdzumpActionAuditVerdict verdict,
        String reason,
        ULong auditId) {
}
