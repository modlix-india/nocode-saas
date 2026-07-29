package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.modlix.saas.adzump.model.Money;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The SLOW, true signal of a {@link SnapshotRow}: the leadzump CRM outcomes for one ad-grain over the
 * window, <b>already folded onto the vertical's milestone keys</b> by the template
 * {@code MilestoneMapping} (J10 §5.2). {@code null} on a {@link SnapshotRow} means no CRM row joined
 * (a {@link SignalMaturity#FAST_ONLY} row).
 *
 * <ul>
 * <li>{@code countByMilestone} — ticket count that reached each milestone (e.g. {@code lead},
 * {@code qualified}, {@code site_visit}, {@code booking}).</li>
 * <li>{@code costByMilestone} — the <b>unit</b> cost per milestone (cost per ticket that reached it,
 * i.e. CPL / CPQL / cost-per-visit), the count-weighted fold of the contributing leadzump raw keys;
 * this is what the {@code PerformancePolicy} per-stage {@code target_cost} is compared against.</li>
 * <li>{@code junkRate} — the leadzump-tagged junk fraction (0..1), passed through from the CRM read.</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
public class CrmMetrics implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293843L;

    private Map<String, Long> countByMilestone;
    private Map<String, Money> costByMilestone;
    private double junkRate;
}
