package com.modlix.saas.adzump.service.optimize;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

/**
 * The product of one optimization run (J12 §5.1): the ranked, gated set of proposed changes for a
 * campaign, computed on-demand from the latest {@link com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot}
 * (J10). Recommend-mode only — every {@link Action#requiresApproval()} is {@code true}; nothing is
 * applied and nothing is persisted in P3 (the approval-queue row is a P4/J13 concern).
 *
 * @param campaignPlanId          the plan this run optimizes.
 * @param snapshotId              the snapshot the diagnosis was read from ({@code null} when computed
 *                                from an unpersisted / absent snapshot).
 * @param generatedAt            when the run was computed.
 * @param actions                 the surviving proposals, <b>ranked by {@link Action#expectedDelta()}
 *                                descending</b>.
 * @param suppressed             candidates the gate rejected, with reasons (explainable no-op).
 * @param objectiveBefore        the current blended objective (J10 PolicyScorer rollup, 0..100).
 * @param objectiveProjectedAfter the projected objective if every action were applied (heuristic delta
 *                                in P3; a TODO swaps in the J20 predictor).
 */
public record ActionSet(
        ULong campaignPlanId,
        ULong snapshotId,
        LocalDateTime generatedAt,
        List<Action> actions,
        List<SuppressedCandidate> suppressed,
        double objectiveBefore,
        double objectiveProjectedAfter) {

    /** An empty result (no snapshot to optimize, or nothing survived the gate). */
    public static ActionSet empty(ULong campaignPlanId, ULong snapshotId, double objectiveBefore) {
        return new ActionSet(campaignPlanId, snapshotId, LocalDateTime.now(), List.of(), List.of(),
                objectiveBefore, objectiveBefore);
    }
}
