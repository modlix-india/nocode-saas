package com.modlix.saas.adzump.model.competition;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.modlix.saas.adzump.vertical.ProxyWeights;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The JSON {@code body} of an {@code adzump_competition_research} row (J19 §5.5). The flat columns
 * (client_code, product_id, vertical, generated_at) carry the addressing + time key; this body carries
 * the finding: the <b>proxy-ranked</b> shortlist of competitor ads with their decomposed scores, the
 * weights used, and the honest caveats — stored verbatim so the Competition tab renders exactly what
 * was computed and can always show <i>why</i> and <i>how</i> it was ranked.
 *
 * <p>Held separate from the DTO (mirrors {@code CampaignPlanBody}/{@code PerformanceSnapshotBody}) so
 * the persistence layer serializes exactly this payload while the addressing fields stay first-class
 * columns.
 *
 * <p>Findings are <b>tenant-private</b>: this body is stored under the client and read only through the
 * tenant gate. Market-level themes are promotable to the shared tier only via the explicit,
 * de-identified promotion path (RETRIEVAL §3.1) — never auto-shared (J19 §5.5), which is why nothing
 * here is written outside the client row.
 */
@Data
@Accessors(chain = true)
public class CompetitionResearchBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 4610293847610293841L;

    private String productId;
    private String vertical;
    private LocalDateTime generatedAt;

    /** Always {@link RankingBasis#PROXY} — labeled so nothing downstream reads it as performance. */
    private RankingBasis rankingBasis;

    /** The advertiser page ids this research mined (the input competitor set, for provenance). */
    private List<String> competitorPageIds;

    /** The proxy weights used for this run (the vertical's own, else defaults). */
    private ProxyWeights weights;

    /** The proxy-ranked shortlist (rank 1 = strongest proxy). */
    private List<RankedCompetitorAd> rankedAds;

    /** Human-readable honest caveats surfaced with the result (J19 §5.4). */
    private List<String> caveats;
}
