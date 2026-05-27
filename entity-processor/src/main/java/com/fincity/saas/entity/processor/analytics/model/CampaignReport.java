package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

/**
 * One node in the campaign report tree. {@code level} disambiguates whether
 * {@code id} refers to a campaign, adset, or ad. {@code children} is populated
 * only when the caller asked to expand into the next level.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CampaignReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 8273441190213557123L;

    public enum Level {
        CAMPAIGN,
        ADSET,
        AD
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class StageCell implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private long count;
        /** Row's spend / count. Null when count is zero or spend is zero. */
        private BigDecimal cpl;
    }

    // ---- identity ----
    private Level level;
    private ULong id;
    /** Platform-side id string (CAMPAIGN_ID / ADSET_ID / AD_ID column). */
    private String externalId;
    private String name;

    // ---- parent context (filled on adset/ad rows) ----
    private ULong campaignId;
    private ULong adsetId;

    // ---- attribute fields useful for UI display ----
    private String platform;
    private ULong productId;
    private String productName;
    /** Ad creative thumbnail URL; populated for AD-level rows that have a creative. */
    private String thumbnailUrl;
    /**
     * True when this row has a creative thumbnail. Bound directly to the Image's
     * visibility, which in Modlix is "visible when true" — so only ad rows with a real
     * thumbnail show the image; campaign/adset rows (default false) hide it. Default
     * false is deliberate so any row that doesn't set it hides the empty image cell.
     */
    private boolean hasCreative;

    // ---- ad-platform metrics ----
    private long impressions;
    private long clicks;
    private BigDecimal spend = BigDecimal.ZERO;
    private long platformWL;
    private long platformFL;

    // ---- computed metrics ----
    private BigDecimal ctr;
    private BigDecimal cpm;
    private BigDecimal cpc;
    private BigDecimal cpl;
    private BigDecimal cpmql;
    private BigDecimal cpsql;
    private BigDecimal cpw;

    // ---- per-stage counts + per-stage CPL, keyed by stage ID (as String) ----
    private Map<String, StageCell> stageCells;
    /** Funnel-stage rollup (LEAD/MQL/SQL/WON/LOST/CUSTOM/UNTAGGED). */
    private Map<String, Long> leadsByFunnelStage;

    // ---- nested tree (null when flat or when this row is a leaf) ----
    private List<CampaignReport> children;
}
