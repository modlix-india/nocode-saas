package com.fincity.saas.entity.processor.analytics.model;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class CampaignReport {

    // Hierarchy identifiers
    private ULong campaignId;
    private ULong adsetId;
    private ULong adId;
    private String campaignName;
    private String campaignPlatform;
    private String adsetName;
    private String adName;
    private String productName;

    // Ad platform metrics (aggregated from campaign_metrics table)
    private long impressions;
    private long clicks;
    private BigDecimal spend = BigDecimal.ZERO;
    private long platformWL;
    private long platformFL;

    // Computed ad metrics
    private BigDecimal ctr = BigDecimal.ZERO;
    private BigDecimal cpm = BigDecimal.ZERO;
    private BigDecimal cpc = BigDecimal.ZERO;
    private BigDecimal cpl = BigDecimal.ZERO;

    // CRM metrics — DYNAMIC stage counts per product template
    private long totalMetaLeads;
    private long totalDcrmLeads;
    private Map<String, Long> leadsByStage;

    // Rejected / Difference
    private long rl;
    private long diff;

    // Derived / computed
    private BigDecimal ncPercent = BigDecimal.ZERO;
    private BigDecimal lostPercent = BigDecimal.ZERO;
    private BigDecimal ncPlusLPercent = BigDecimal.ZERO;
    private BigDecimal lcPercent = BigDecimal.ZERO;
}
