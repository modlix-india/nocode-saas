package com.modlix.saas.adzump.service.creative;

import java.util.OptionalDouble;

/**
 * The J19 market-priors seam (J20 §5.2/§5.3): shared, cross-account attribute performance the loop
 * <b>seeds</b> attribution and prediction with before an account has its own history (the cold-start
 * layer). J19 is not built yet — the default binding is {@link EmptyMarketPriors} (returns nothing), so
 * J20 falls back to the account baseline / vertical defaults. When J19 lands it supplies a real
 * implementation; nothing else in J20 changes.
 *
 * <p><b>Tenancy.</b> Priors are the <i>shared</i> layer (RETRIEVAL §3): they are market-level, promoted
 * explicitly, and never leak one account's private wins into another. An account's own realized
 * outcomes (the tenant-private map) always dominate as volume grows.
 */
public interface MarketPriors {

    /**
     * The prior expected 0..100 performance score for an attribute value in a vertical, or
     * {@link OptionalDouble#empty()} when no prior is known (the value is genuinely under-explored).
     */
    OptionalDouble priorScore(String vertical, String axis, String value);

    /**
     * The prior baseline (0..100) an average creative scores in this vertical, used as the cold-start
     * anchor when the account has no realized volume yet. Empty when unknown.
     */
    OptionalDouble priorBaseline(String vertical);
}
