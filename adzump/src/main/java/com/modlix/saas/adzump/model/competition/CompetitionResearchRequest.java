package com.modlix.saas.adzump.model.competition;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The input to a J19 research run for a product. Bundles the discovered competitor list with the
 * market context the mining + proxy need:
 * <ul>
 *   <li>{@link #vertical} — resolves the vertical-tunable proxy weights + taxonomy (J5).</li>
 *   <li>{@link #competitors} — the advertisers to pull running ads for (A2's discovery; input here).</li>
 *   <li>{@link #keywords} — optional keyword/category seeds for a market-level (non-advertiser) search.</li>
 *   <li>{@link #reachedCountries} — the Ad Library {@code ad_reached_countries} filter (the product's market).</li>
 * </ul>
 *
 * <p>Carried as a request object (rather than five positional args) because the middle argument of
 * {@code research(productId, request, targetClientCode)} <i>is</i> "the competitor list + its market" —
 * the same shape the {@code CampaignPlan}/{@code Creative} services take.
 */
@Data
@Accessors(chain = true)
public class CompetitionResearchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5610293847610293841L;

    /** The product's vertical (J5) — resolves proxy weights and taxonomy. */
    private String vertical;

    /** Discovered competitors (advertiser pages) to mine. May be empty (then only {@link #keywords} run). */
    private List<Competitor> competitors;

    /** Optional keyword/category seeds for a market-level Ad Library search across advertisers. */
    private List<String> keywords;

    /** {@code ad_reached_countries} filter; when empty the client applies its configured default. */
    private List<String> reachedCountries;
}
