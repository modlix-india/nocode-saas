package com.modlix.saas.adzump.model.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The J10 fact everything downstream optimizes on: a durable, time-stamped picture of one campaign
 * over a window, where each grain row merges the FAST platform signal with the SLOW leadzump CRM
 * signal (joined by ad-grain id) and carries a blended {@code PerformancePolicy} score.
 *
 * <p>This is the rich domain aggregate produced by the {@code SnapshotBuilder} and returned by the
 * {@code FeedbackService} reads. It is persisted append-only (never mutated): each build appends a
 * new {@link #takenAt} so the loop (J12/A5) sees the trend as slow signal matures, not just a point
 * (J10 §5.1 / §5.5). {@link #id} is null until persisted.
 */
@Data
@Accessors(chain = true)
public class PerformanceSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 7301928476510293846L;

    /** the stored snapshot row id; null until persisted. */
    private ULong id;

    private ULong campaignPlanId;
    private String clientCode;

    /** the leadzump product template the CRM outcomes were folded against. */
    private String productTemplateId;

    private SnapshotWindow window;

    /** when this snapshot was taken; immutable, the append-only time-series key. */
    private LocalDateTime takenAt;

    /** the rolled-up 0..100 blended score across the coarsest-grain rows (J10 §5.4). */
    private double rollupScore;

    private List<SnapshotRow> grainRows;
}
