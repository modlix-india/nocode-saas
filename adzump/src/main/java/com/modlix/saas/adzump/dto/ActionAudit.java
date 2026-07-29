package com.modlix.saas.adzump.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * J13 §5.4 — one row of the {@code adzump_action_audit} trail: the reversible record of every routing /
 * apply decision (applied / queued / suppressed / rejected / reversed) with its before/after values, the
 * snapshot it was measured against, who/what triggered it, and a note. The queue itself is this table —
 * a {@code QUEUED} row is a pending recommendation a human can approve (flip to apply) or reject.
 *
 * <p>Maps 1:1 onto the pre-existing {@code adzump_action_audit} table (JOOQ {@code AdzumpActionAuditRecord}).
 * The table has no {@code updated_at}/{@code created_by}/{@code updated_by} columns (audit rows are
 * append-only, never updated in place through the DTO), so those inherited fields stay unmapped; only
 * {@code created_at} is persisted. {@code beforeValue}/{@code afterValue} are the JSON columns.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class ActionAudit extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 8471625093812746510L;

    private String clientCode;
    private ULong campaignPlanId;
    private AdzumpActionAuditActionType actionType;
    private AdzumpActionAuditVerdict verdict;
    private AdzumpActionAuditTriggeredBy triggeredBy;
    private JsonNode beforeValue;
    private JsonNode afterValue;
    private ULong snapshotId;
    private String notes;
}
