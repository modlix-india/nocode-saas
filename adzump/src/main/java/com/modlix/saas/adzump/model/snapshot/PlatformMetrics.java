package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.model.Money;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The FAST signal of a {@link SnapshotRow}: the platform-reported metrics for one ad-grain over the
 * window (J10 §5.1). {@code ctr} and {@code cpc} are derived by the builder from the raw
 * impressions/clicks/spend the SPI returns, so the app and the scorer do not each recompute them.
 * {@code platformConversions} is the platform's OWN attributed conversions and is kept distinct from
 * the CRM-attributed outcomes joined in {@link SnapshotRow#getCrm()}.
 */
@Data
@Accessors(chain = true)
public class PlatformMetrics implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293842L;

    private long impressions;
    private long clicks;
    private Money spend;

    /** clicks / impressions (0 when there were no impressions). */
    private double ctr;

    /** spend / clicks as a per-click cost in the spend currency (null when there were no clicks). */
    private Money cpc;

    /** the platform's own attributed conversions (NOT the CRM outcomes). */
    private long platformConversions;
}
