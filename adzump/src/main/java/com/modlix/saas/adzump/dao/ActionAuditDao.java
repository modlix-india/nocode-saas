package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpActionAudit.ADZUMP_ACTION_AUDIT;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.ActionAudit;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpActionAuditRecord;

/**
 * J13 §5.4 — the {@code adzump_action_audit} store: the append-only trail of every routing/apply
 * decision, and (as the {@code QUEUED} rows) the approval queue itself. Extends
 * {@link AbstractAdzumpJsonDAO} so the two JSON columns ({@code before_value}, {@code after_value}) map
 * through the shared JSON hook while the rest map via JOOQ. Rows are written once ({@link #create}); the
 * only in-place mutation is {@link #linkSnapshot} (§5.4 close-the-loop), a targeted column update rather
 * than a full-record rewrite (the table has no {@code updated_at}/{@code created_by} columns).
 */
@Service
public class ActionAuditDao extends AbstractAdzumpJsonDAO<AdzumpActionAuditRecord, ActionAudit> {

    public ActionAuditDao() {
        super(ActionAudit.class, ADZUMP_ACTION_AUDIT, ADZUMP_ACTION_AUDIT.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, ActionAudit pojo) {
        pojo.setBeforeValue(fromJson(getJson(rec, ADZUMP_ACTION_AUDIT.BEFORE_VALUE), JsonNode.class));
        pojo.setAfterValue(fromJson(getJson(rec, ADZUMP_ACTION_AUDIT.AFTER_VALUE), JsonNode.class));
    }

    @Override
    protected void writeCustomColumns(ActionAudit pojo, AdzumpActionAuditRecord rec) {
        rec.set(ADZUMP_ACTION_AUDIT.BEFORE_VALUE, toJson(pojo.getBeforeValue()));
        rec.set(ADZUMP_ACTION_AUDIT.AFTER_VALUE, toJson(pojo.getAfterValue()));
        // created_at is NOT NULL; guarantee a value even if a caller forgot to set one (append-only rows).
        if (pojo.getCreatedAt() == null)
            rec.set(ADZUMP_ACTION_AUDIT.CREATED_AT, LocalDateTime.now());
    }

    /**
     * The recent {@code APPLIED} rows for a campaign within the effective client, newest first — the
     * history the rate/frequency guardrail reads to find when each entity was last changed. When
     * {@code since} is non-null only rows at/after it are returned.
     */
    public List<ActionAudit> recentApplied(String clientCode, ULong campaignPlanId, LocalDateTime since) {

        Condition condition = ADZUMP_ACTION_AUDIT.CLIENT_CODE.eq(clientCode)
                .and(ADZUMP_ACTION_AUDIT.CAMPAIGN_PLAN_ID.eq(campaignPlanId))
                .and(ADZUMP_ACTION_AUDIT.VERDICT.eq(AdzumpActionAuditVerdict.APPLIED));
        if (since != null)
            condition = condition.and(ADZUMP_ACTION_AUDIT.CREATED_AT.ge(since));

        return this.dslContext.selectFrom(ADZUMP_ACTION_AUDIT)
                .where(condition)
                .orderBy(ADZUMP_ACTION_AUDIT.CREATED_AT.desc())
                .fetch()
                .map(this::toPojo);
    }

    /**
     * Links applied audit rows to the fresh post-apply snapshot (§5.4): the action &rarr; outcome-delta
     * connection the loop learns from. A targeted {@code snapshot_id} update, not a full-record rewrite.
     */
    public void linkSnapshot(List<ULong> auditIds, ULong snapshotId) {

        if (auditIds == null || auditIds.isEmpty() || snapshotId == null)
            return;

        this.dslContext.update(ADZUMP_ACTION_AUDIT)
                .set(ADZUMP_ACTION_AUDIT.SNAPSHOT_ID, snapshotId)
                .where(ADZUMP_ACTION_AUDIT.ID.in(auditIds))
                .execute();
    }
}
