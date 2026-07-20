package com.modlix.saas.adzump.model.snapshot;

/**
 * The fast/slow-signal split of a {@link SnapshotRow} (J10 §5.3): how far the CRM (leadzump) truth
 * has matured for this ad-grain, which the optimizer (J12/J13) uses to decide whether a kill is
 * trustworthy yet.
 *
 * <ul>
 * <li>{@link #FAST_ONLY} — platform spend has landed but no CRM outcome has arrived yet (or the
 * joined CRM row is empty). Only the fast platform signal exists; a killing decision must not rely on
 * it alone.</li>
 * <li>{@link #PARTIAL} — some entry-milestone leads have arrived but no downstream milestone has
 * reached its policy volume gate ({@code min_count}); the slow signal is forming but is still too
 * thin to trust a kill.</li>
 * <li>{@link #MATURE} — at least one downstream milestone has reached its policy volume gate, so the
 * blended score is trustworthy enough to act on (including a kill).</li>
 * </ul>
 */
public enum SignalMaturity {
    FAST_ONLY,
    PARTIAL,
    MATURE
}
