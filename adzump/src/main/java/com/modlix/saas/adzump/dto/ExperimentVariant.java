package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One arm of a creative {@link Experiment} (J21 §5.1): a creative (from A4) plus the {@code axis -> value}
 * attribute tags it carries and the fraction of the experiment's traffic/budget it is allocated. Variants
 * are chosen to <b>vary exactly one attribute axis</b> while holding the rest constant (the isolation
 * {@code ExperimentService.design} enforces), so the readout's lift is attributable to <i>that</i>
 * attribute, not a confounded tangle.
 *
 * <p>Persisted inside the {@code adzump_experiment.variants} JSON column (a list of these). A mutable
 * Lombok bean rather than a record so it round-trips cleanly through the DAO's shared Jackson mapper
 * without relying on the {@code -parameters} compiler flag (the same convention as the other JSON bodies).
 */
@Data
@Accessors(chain = true)
public class ExperimentVariant implements Serializable {

    @Serial
    private static final long serialVersionUID = 5729103846571029384L;

    /** The plan-body creative id this arm runs (matched to the snapshot AD grain by {@code adId}). */
    private String creativeId;

    /** The creative's {@code axis -> value} attribute tags (J5 taxonomy); the isolated axis is the one under test. */
    private Map<String, String> attributes;

    /** The fraction (0..1) of the experiment allocated to this arm; even across arms by default. */
    private Double allocation;
}
