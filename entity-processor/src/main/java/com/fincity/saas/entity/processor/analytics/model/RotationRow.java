package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.CampaignReport.StageCell;
import com.fincity.saas.entity.processor.util.DatePair;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One row in the creative rotation report time series table (Daily / Weekly / Monthly).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RotationRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Formatted period label (e.g. "2026-08-21", "Wk of Aug 18", "Aug 2026"). */
    private String period;

    /** Start and end bounds of this time period in local timezone. */
    private DatePair periodBounds;

    /** Platform spend in period. */
    private BigDecimal spend = BigDecimal.ZERO;

    /** Spend share: period spend ÷ grand total spend across the displayed range. */
    private BigDecimal share = BigDecimal.ZERO;

    private long impressions;

    private long clicks;

    /** Click-through rate: clicks ÷ impressions * 100. */
    private BigDecimal ctr;

    /** Platform self-reported form leads (from Meta/Google ad metrics). */
    private long platformFl;

    /** Platform self-reported web leads (from Meta/Google ad metrics). */
    private long platformWl;

    /** Funnel stage cells containing lead count and per-stage CPL. */
    private Map<String, StageCell> stageCells;
}
