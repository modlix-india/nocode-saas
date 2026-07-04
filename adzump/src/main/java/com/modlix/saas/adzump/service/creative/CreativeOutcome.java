package com.modlix.saas.adzump.service.creative;

import java.util.Map;

/**
 * One creative's realized outcome joined to its attribute tags — the unit
 * {@link AttributeAttributor#aggregate} decomposes onto attribute values. Produced by combining a
 * {@link CreativeScore} (score/volume/junk/maturity, from J10) with the creative's
 * {@code axis -> value} tag map (J5 taxonomy, stored in {@code adzump_creative_attribute}).
 *
 * <p>Kept as the explicit, self-contained input to the attribution math so the regularization,
 * junk-correlation, and cold-start logic can be unit-tested from hand-built fixtures with no snapshot,
 * DAO, or security plumbing.
 *
 * @param creativeId the plan-body creative id.
 * @param attributes the creative's {@code axis -> value} tags (J5 taxonomy); empty if untagged.
 * @param score      the creative's 0..100 blended score.
 * @param volume     the creative's CRM outcome count (its statistical weight).
 * @param junkRate   the creative's volume-weighted leadzump junk fraction (0..1).
 * @param judgeable  whether the creative's slow signal has matured (see {@link CreativeScore#judgeable()}).
 */
public record CreativeOutcome(
        String creativeId,
        Map<String, String> attributes,
        double score,
        long volume,
        double junkRate,
        boolean judgeable) {

    public CreativeOutcome {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Builds an outcome from a {@link CreativeScore} plus the creative's attribute tags. */
    public static CreativeOutcome of(CreativeScore score, Map<String, String> attributes) {
        return new CreativeOutcome(score.creativeId(), attributes, score.score(), score.volume(),
                score.junkRate(), score.judgeable());
    }
}
