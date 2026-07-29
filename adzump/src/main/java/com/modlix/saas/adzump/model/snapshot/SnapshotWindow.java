package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The metrics window a {@link PerformanceSnapshot} is taken over (J10 §5.1). The same {@code from} /
 * {@code to} / {@code timezone} is carried on <b>both</b> the platform {@code insights} read and the
 * leadzump {@code getOutcomes} read, so the fast (platform) and slow (CRM) numbers are directly
 * comparable — the join at the ad grain would otherwise mix windows.
 */
@Data
@Accessors(chain = true)
public class SnapshotWindow implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293841L;

    /** Inclusive start date. */
    private LocalDate from;

    /** Inclusive end date. */
    private LocalDate to;

    /** The account timezone the window is expressed in (platform insights are tz-sensitive). */
    private String timezone;
}
