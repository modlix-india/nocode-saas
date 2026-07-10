package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One grain row of a {@link PerformanceSnapshot}: the FAST platform signal
 * ({@link #platform}) left-joined to the SLOW leadzump CRM signal ({@link #crm}) <b>by the ad-grain
 * id</b> (J10 §5.2 — the differentiator; the legacy name-join is insufficient). {@link #crm} is
 * {@code null} when the platform row has no CRM outcome yet (a {@link SignalMaturity#FAST_ONLY} row).
 *
 * @see SignalMaturity for the fast/slow split
 */
@Data
@Accessors(chain = true)
public class SnapshotRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293844L;

    /** the grain this row is reported at (CAMPAIGN | ADSET | AD). */
    private Grain grain;

    /** the platform-real ad-grain id this row is joined on. */
    private AdGrainId adGrainId;

    /** the FAST platform signal (never null — a snapshot row starts from a platform insight). */
    private PlatformMetrics platform;

    /** the SLOW leadzump signal, folded onto milestone keys; null when nothing joined yet. */
    private CrmMetrics crm;

    private SignalMaturity signalMaturity;

    /** the 0..100 blended {@code PerformancePolicy} score (J10 §5.4). */
    private double blendedScore;
}
