package com.modlix.saas.adzump.model.competition;

import java.time.LocalDate;
import java.util.List;

import com.modlix.saas.adzump.vertical.ProxyWeights;

/**
 * The market context a single ad is scored against (J19 §5.2). It carries the vertical-tunable
 * {@link ProxyWeights}, the {@code asOf} reference date that anchors longevity/recency (injected, not
 * {@code LocalDate.now()}, so scoring is deterministic and testable), and the full {@code corpus} of
 * competitor ads under consideration — because two of the five signals are <b>market-level</b>:
 * iteration density (same advertiser + theme across the corpus) and breadth (the same theme across
 * multiple competitors). Scoring one ad therefore needs to see its peers.
 *
 * @param vertical the product's vertical (for provenance/labeling; weights already resolved into {@code weights}).
 * @param asOf     the "now" anchor for longevity (start→asOf) and recency; never null in practice.
 * @param weights  the resolved proxy weights (vertical's own, else {@link ProxyWeights#defaults()}).
 * @param corpus   every ad in the ranking run, so market-level signals can be computed per ad.
 */
public record MarketContext(
        String vertical,
        LocalDate asOf,
        ProxyWeights weights,
        List<CompetitorAd> corpus) {

    public MarketContext {
        corpus = corpus == null ? List.of() : List.copyOf(corpus);
    }
}
