package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.jooq.enums.AdzumpExperimentStatus;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * J21 §5.1 — one row of {@code adzump_experiment}: a controlled creative experiment on a campaign plan. It
 * carries the hypothesis, the {@code metric} (the blended objective at the creative grain), the volume /
 * duration caps that bound it, the {@link ExperimentVariant} arms (varying exactly one attribute axis), and
 * the computed {@link ExperimentReadout}.
 *
 * <p>Maps 1:1 onto the {@code adzump_experiment} table (JOOQ {@code AdzumpExperimentRecord}). {@code status}
 * is the generated enum column; {@code variants} and {@code readout} are the JSON columns (handled by the
 * DAO's custom-column hooks). The {@code created_by/at} + {@code updated_by/at} audit columns are the
 * inherited {@link AbstractUpdatableDTO} fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Experiment extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 7410293856102938471L;

    /** Tenant-private client scope (pinned server-side from the plan, never trusted from a body). */
    private String clientCode;

    /** The campaign plan this experiment runs on. */
    private ULong campaignPlanId;

    /** The human hypothesis under test (e.g. "possession-ready angle beats investor angle for end-users"). */
    private String hypothesis;

    /** The success metric — the blended objective at the creative grain (default {@code blendedScore@creative}). */
    private String metric;

    /** Minimum CRM outcome volume per arm before a readout may reach significance. */
    private Integer minVolumePerVariant;

    /** Hard duration cap (days) — the experiment ends INCONCLUSIVE at this cap rather than running unbounded. */
    private Integer maxDurationDays;

    /** DESIGNED | RUNNING | SIGNIFICANT | INCONCLUSIVE | APPLIED. */
    private AdzumpExperimentStatus status;

    /** The experiment arms (JSON column) — each varies the one isolated attribute axis. */
    private List<ExperimentVariant> variants;

    /** The computed readout (JSON column) — {@code null} until first measured. */
    private ExperimentReadout readout;

    /** When the experiment started rotating live (RUNNING); {@code null} while DESIGNED. */
    private LocalDateTime startedAt;

    /** When the experiment reached a terminal readout; {@code null} while DESIGNED/RUNNING. */
    private LocalDateTime endedAt;
}
