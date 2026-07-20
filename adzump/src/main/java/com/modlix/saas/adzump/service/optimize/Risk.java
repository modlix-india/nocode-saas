package com.modlix.saas.adzump.service.optimize;

/**
 * The blast radius of an {@link Action} if applied (J12 §5.1). Together with the
 * {@link SignificanceVerdict} it drives J13's autonomy routing (P4): low-risk actions may auto-apply
 * in HYBRID/AUTONOMOUS, higher-risk ones queue for approval. In P3 (recommend-mode) every action
 * carries {@code requiresApproval = true} regardless of risk — nothing auto-applies.
 *
 * <ul>
 * <li>{@link #LOW} — additive / reversible and cheap: a negative keyword, a small budget trim, a
 * creative rotation. Safe even on fast-only signal.</li>
 * <li>{@link #MED} — reallocates spend or pauses obvious zero-outcome waste; reversible but moves
 * money.</li>
 * <li>{@link #HIGH} — kills a converter or makes a hard-to-reverse change; only ever proposed on
 * {@code MATURE} signal (the significance gate enforces this).</li>
 * </ul>
 */
public enum Risk {
    LOW,
    MED,
    HIGH
}
