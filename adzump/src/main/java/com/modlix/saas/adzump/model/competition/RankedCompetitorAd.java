package com.modlix.saas.adzump.model.competition;

/**
 * A competitor ad paired with its {@link ProxyScore} and its 1-based rank within the shortlist. This
 * is the unit of the J19 output and of the persisted body: a <b>proxy-ranked</b> (never
 * performance-ranked) list. {@code rank} is assigned by descending {@link ProxyScore#total}.
 *
 * @param rank  1-based position in the proxy-ranked shortlist (1 = strongest proxy).
 * @param ad    the competitor ad.
 * @param score its decomposed proxy score.
 */
public record RankedCompetitorAd(int rank, CompetitorAd ad, ProxyScore score) {
}
