package com.modlix.saas.adzump.service.optimize;

import java.util.List;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.service.feedback.PolicyScorer;

/**
 * What "better" means (J12 §5.4): the objective is the blended {@code PerformancePolicy} score — the
 * same 0..100 number J10's {@link PolicyScorer} produces and A5 explains. The engine ranks actions by
 * their {@link Action#expectedDelta()} on this objective and reports the projected after-value.
 *
 * <p>{@link #before(PerformanceSnapshot)} re-derives the current objective from the snapshot rows via
 * the scorer (so it is self-consistent with the number the loop optimizes). The projected delta is a
 * conservative <b>heuristic</b> in P3 — each analyzer estimates its own {@code expectedDelta} from
 * local economics. <b>TODO(J20):</b> replace the summed heuristic with the calibrated J20 predictor
 * (ML, never an LLM).
 */
@Service
public class Objective {

    private final PolicyScorer policyScorer;

    public Objective(PolicyScorer policyScorer) {
        this.policyScorer = policyScorer;
    }

    /** The current blended objective for a snapshot (rolled up over the coarsest grain, spend-weighted). */
    public double before(PerformanceSnapshot snapshot) {
        if (snapshot == null || snapshot.getGrainRows() == null || snapshot.getGrainRows().isEmpty())
            return snapshot == null ? 0.0d : snapshot.getRollupScore();
        return this.policyScorer.rollup(snapshot.getGrainRows());
    }

    /**
     * The projected objective if every ranked action were applied: {@code before} plus the summed
     * per-action heuristic deltas, clamped to 0..100. In P3 the actions are proposed independently and
     * their deltas summed (interactions are modelled later; J13 bounds coupling with
     * max-change-per-run).
     */
    public double projectedAfter(double before, List<Action> actions) {
        double after = before;
        if (actions != null)
            for (Action a : actions)
                after += a.expectedDelta();
        return Math.max(0.0d, Math.min(100.0d, after));
    }
}
