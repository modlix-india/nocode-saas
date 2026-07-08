package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One variant's measured outcome inside an {@link ExperimentReadout} (J21 §5.1): the creative-grain blended
 * score (J10/J20 {@code getScore}) with the CRM outcome {@code volume} that is its statistical weight, a
 * Wald confidence interval around the score (in score units, 0..100), and the {@link SignalMaturity} that
 * says whether the slow leadzump signal has matured enough to <b>trust</b> the number.
 *
 * <p>{@code judgeable} mirrors {@link com.modlix.saas.adzump.service.creative.CreativeScore#judgeable()} —
 * {@code true} only on {@link SignalMaturity#MATURE}. A readout cannot declare a winner while any arm is
 * not judgeable (the maturity gate), so this is the field that turns a premature "win" into
 * {@code INCONCLUSIVE}. Persisted inside the {@code adzump_experiment.readout} JSON column.
 */
@Data
@Accessors(chain = true)
public class VariantOutcome implements Serializable {

    @Serial
    private static final long serialVersionUID = 8471029385610293847L;

    private String creativeId;

    /** The variant's 0..100 blended objective score at the creative grain (the experiment metric). */
    private double score;

    /** CRM outcome count — the statistical weight of this arm's evidence (0 when only fast signal has landed). */
    private long volume;

    /** Lower bound of the score's 95% Wald confidence interval (score units, clamped to [0,100]). */
    private double ciLow;

    /** Upper bound of the score's 95% Wald confidence interval (score units, clamped to [0,100]). */
    private double ciHigh;

    /** The arm's CRM signal maturity (the coarsest-trust across the matched rows). */
    private SignalMaturity maturity;

    /** {@code true} iff {@link #maturity} is {@link SignalMaturity#MATURE} — the maturity gate's per-arm bit. */
    private boolean judgeable;
}
