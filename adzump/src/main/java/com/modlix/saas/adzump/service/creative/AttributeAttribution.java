package com.modlix.saas.adzump.service.creative;

import java.util.ArrayList;
import java.util.List;

import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;

/**
 * The account's living <b>attribute performance map</b> (J20 §5.2) for one vertical over a window: the
 * decomposition of realized creative outcomes onto the J5 attribute taxonomy, plus the explicit
 * explore/exploit surface (§5.4) the loop (A4/J21) steers on.
 *
 * <p><b>Exploit</b> = {@link #winners()}: the confident, positive-lift, breadth-backed, junk-clean
 * values worth doubling down on. <b>Explore</b> = {@link #underExplored()}: values with too little
 * evidence to trust yet (thin observed values plus taxonomy values never tried), which A4 should
 * generate into and J21 should design experiments around.
 *
 * <p>{@link #coldStart()} is true when the account has no matured realized volume yet, so the map leans
 * on market priors / vertical defaults and the predictor surfaces low confidence (A4 soft-ranks rather
 * than hard-blocks — §5.3).
 *
 * @param clientCode the tenant this map belongs to (tenant-private — §5.5).
 * @param vertical   the vertical the taxonomy + priors were resolved for.
 * @param window     the outcome window the decomposition was computed over (may be null for on-demand).
 * @param baseline   the account (or cold-start prior) baseline 0..100 lifts are measured against.
 * @param stats      the per-value standings observed from realized outcomes.
 * @param unexplored taxonomy values with no realized evidence yet (the pure-explore frontier).
 * @param coldStart  whether this map is prior/default-seeded rather than realized-outcome-backed.
 */
public record AttributeAttribution(
        String clientCode,
        String vertical,
        SnapshotWindow window,
        double baseline,
        List<AttributeStat> stats,
        List<AttributeStat> unexplored,
        boolean coldStart) {

    public AttributeAttribution {
        stats = stats == null ? List.of() : List.copyOf(stats);
        unexplored = unexplored == null ? List.of() : List.copyOf(unexplored);
    }

    /** The observed standing of {@code axis=value}, or {@code null} if it has no realized evidence. */
    public AttributeStat stat(String axis, String value) {
        if (axis == null || value == null)
            return null;
        for (AttributeStat s : this.stats)
            if (axis.equals(s.axis()) && value.equals(s.value()))
                return s;
        return null;
    }

    /** Exploit surface: the winning values, worth doubling down on. */
    public List<AttributeStat> winners() {
        List<AttributeStat> out = new ArrayList<>();
        for (AttributeStat s : this.stats)
            if (s.winner())
                out.add(s);
        return out;
    }

    /**
     * Explore surface: the values that need more evidence — thin observed values plus the taxonomy
     * values never tried ({@link #unexplored()}). A4 generates into these; J21 designs experiments.
     */
    public List<AttributeStat> underExplored() {
        List<AttributeStat> out = new ArrayList<>();
        for (AttributeStat s : this.stats)
            if (s.underExplored())
                out.add(s);
        out.addAll(this.unexplored);
        return out;
    }
}
