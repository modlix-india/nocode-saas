package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshotBody;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * The persistence row for {@code adzump_performance_snapshot} (J1). The flat columns carry the
 * addressing + the time-series key; the JSON {@code body} carries the grain-row metrics payload
 * ({@link PerformanceSnapshotBody}). Append-only: every build inserts a new row with a fresh
 * {@link #getTakenAt() takenAt}, never mutating a prior snapshot (J10 §5.5).
 *
 * <p>Kept distinct from the rich {@code model.snapshot.PerformanceSnapshot} domain aggregate (which
 * nests the window and holds no audit columns) so the DAO maps the plain columns by name and the
 * body via the JSON hook — the same split as {@code CampaignPlan} / {@code CampaignPlanBody}. The
 * snapshot table carries no {@code created_*}/{@code updated_*} columns, so the inherited audit
 * fields stay unmapped.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class PerformanceSnapshotEntity extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -7301928476510293841L;

    private String clientCode;
    private ULong campaignPlanId;
    private LocalDate windowFrom;
    private LocalDate windowTo;
    private String timezone;
    private LocalDateTime takenAt;
    private PerformanceSnapshotBody body;
}
