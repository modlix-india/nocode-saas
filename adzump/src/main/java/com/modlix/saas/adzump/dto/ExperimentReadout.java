package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The statistical result of a creative {@link Experiment} (J21 §5.1/§5.3): each arm's
 * {@link VariantOutcome}, the {@code winner} creative (present only when the readout is a trustworthy,
 * mature win), the two-tailed {@code pValue} of the leading arm against the runner-up, and the
 * {@code significant} flag.
 *
 * <p><b>Maturity-aware.</b> {@link #significant} is the trustworthy-win flag, not merely the statistical
 * test: it is {@code true} only when the leading arm clears the significance test <b>and</b> every arm has
 * reached its minimum volume <b>and</b> the signal has matured (all arms judgeable). {@link #pValue} is the
 * pure statistic and is always populated so the studio can show "separated but not yet mature". When
 * {@code significant} is {@code false} the {@code winner} is {@code null} — no false learning is recorded
 * (the {@code INCONCLUSIVE} outcome). Persisted inside the {@code adzump_experiment.readout} JSON column.
 */
@Data
@Accessors(chain = true)
public class ExperimentReadout implements Serializable {

    @Serial
    private static final long serialVersionUID = 6103928475610293846L;

    /** Each arm's measured outcome, in the experiment's variant order. */
    private List<VariantOutcome> perVariant;

    /** The winning creative id — populated only when {@link #significant} (a trustworthy, mature win). */
    private String winner;

    /** Two-tailed p-value of the leading arm vs the runner-up (two-proportion z-test); always populated. */
    private Double pValue;

    /** {@code true} iff statistically significant AND all arms met min volume AND all arms are mature. */
    private boolean significant;

    /** When this readout was computed. */
    private LocalDateTime computedAt;
}
