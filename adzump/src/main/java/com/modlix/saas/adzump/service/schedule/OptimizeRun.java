package com.modlix.saas.adzump.service.schedule;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.apply.ApplyDecision;
import com.modlix.saas.adzump.service.apply.ApplyResult;

/**
 * The result of one J14 loop execution (J14 §5.1) — a <b>RETURNED record, NO table</b>. Idempotency +
 * dedup ride on the J13 audit (an already-applied ActionSet is not double-applied), so this only reports
 * what the run did: the campaign, the window it ran over (in the account tz), the diagnosis snapshot id,
 * the applied / queued / suppressed counts, and a per-run id for correlation/logging.
 *
 * @param campaignId      the campaign the loop ran for.
 * @param window          the window it ran over (account-tz "yesterday" unless overridden).
 * @param snapshotId      the J10 diagnosis snapshot the run built/read, or {@code null}.
 * @param appliedCount    actions that ended {@code APPLIED} this run.
 * @param queuedCount     actions {@code QUEUED} (kill-switch held, guardrail downgrade, or recommend-mode).
 * @param suppressedCount actions {@code SUPPRESSED} (gate/guardrail refusal).
 * @param runId           an opaque per-run id for correlation.
 */
public record OptimizeRun(
        ULong campaignId,
        SnapshotWindow window,
        ULong snapshotId,
        int appliedCount,
        int queuedCount,
        int suppressedCount,
        String runId) {

    /** Tallies an {@link ApplyResult}'s decisions by verdict into an {@link OptimizeRun}. */
    static OptimizeRun of(ULong campaignId, SnapshotWindow window, ULong snapshotId, ApplyResult result,
            String runId) {

        int applied = 0;
        int queued = 0;
        int suppressed = 0;

        if (result != null && result.decisions() != null)
            for (ApplyDecision decision : result.decisions()) {
                AdzumpActionAuditVerdict verdict = decision.verdict();
                if (verdict == AdzumpActionAuditVerdict.APPLIED)
                    applied++;
                else if (verdict == AdzumpActionAuditVerdict.QUEUED)
                    queued++;
                else if (verdict == AdzumpActionAuditVerdict.SUPPRESSED)
                    suppressed++;
            }

        return new OptimizeRun(campaignId, window, snapshotId, applied, queued, suppressed, runId);
    }
}
