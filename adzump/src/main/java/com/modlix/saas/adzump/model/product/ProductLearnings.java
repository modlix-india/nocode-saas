package com.modlix.saas.adzump.model.product;

import java.util.List;

/**
 * A lean, DE-IDENTIFIED, generalized summary of what the loop has learned works for a product -
 * winning audience descriptors, winning creative angles, and junk-lead patterns. Folded onto the
 * entity-processor product profile by {@code ProductEnhancementService.foldLearnings} (J9) as a
 * controlled, explicit write; it is NOT raw metrics or PII dumped from CRM rows (RETRIEVAL
 * no-auto-promotion).
 *
 * <p>P2 shape: a lean record. The real learnings feed (structured, per-completed-loop-cycle) lands
 * with J10 (feedback) / J20 (creative scoring); the entries here are generalized labels only (no
 * ticket ids, no lead identities, no per-row spend).
 *
 * @param winningAudiences generalized audience descriptors that convert for this product.
 * @param winningAngles    creative angles / value-prop framings that perform.
 * @param junkPatterns     declared-intent / audience patterns that predict junk leads.
 */
public record ProductLearnings(
        List<String> winningAudiences,
        List<String> winningAngles,
        List<String> junkPatterns) {
}
