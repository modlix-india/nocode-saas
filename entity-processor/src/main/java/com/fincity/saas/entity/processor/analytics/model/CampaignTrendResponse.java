package com.fincity.saas.entity.processor.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.analytics.model.CampaignReport.StageCell;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.util.DatePair;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTrendResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StageNode> stageTree;
    private List<CampaignTrendRow> rows;

    /**
     * One row in the campaign trend report time series table (Daily / Weekly / Monthly / Quarterly / Yearly).
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class CampaignTrendRow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
        private static final int SCALE = 2;

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
        private Map<String, StageCell> stageCells = new HashMap<>();

        public CampaignTrendRow addMetric(CampaignMetric metric) {
            if (metric == null) return this;
            if (metric.getSpend() != null && metric.getSpend().signum() != 0) {
                this.spend = this.spend.add(metric.getSpend());
            }
            this.impressions += metric.getImpressions();
            this.clicks += metric.getClicks();
            this.platformFl += metric.getPlatformFL();
            this.platformWl += metric.getPlatformWL();
            return this;
        }

        public CampaignTrendRow addStageCount(String stageId, long count) {
            if (stageId != null && count > 0) {
                StageCell cell = this.stageCells.get(stageId);
                if (cell == null) {
                    cell = new StageCell();
                    this.stageCells.put(stageId, cell);
                }
                cell.setCount(cell.getCount() + count);
            }
            return this;
        }

        public CampaignTrendRow applyRatios(BigDecimal grandTotalSpend) {
            if (grandTotalSpend != null && grandTotalSpend.signum() > 0 && this.spend != null && this.spend.signum() > 0) {
                this.share = this.spend.multiply(HUNDRED).divide(grandTotalSpend, SCALE, RoundingMode.HALF_UP);
            } else {
                this.share = BigDecimal.ZERO;
            }

            if (this.impressions > 0) {
                if (this.clicks > 0) {
                    this.ctr = BigDecimal.valueOf(this.clicks)
                            .multiply(HUNDRED)
                            .divide(BigDecimal.valueOf(this.impressions), SCALE, RoundingMode.HALF_UP);
                } else {
                    this.ctr = BigDecimal.ZERO;
                }
            }

            if (this.spend != null && this.spend.signum() > 0 && this.stageCells != null && !this.stageCells.isEmpty()) {
                for (StageCell cell : this.stageCells.values()) {
                    if (cell.getCount() > 0) {
                        cell.setCpl(this.spend.divide(BigDecimal.valueOf(cell.getCount()), SCALE, RoundingMode.HALF_UP));
                    }
                }
            }
            return this;
        }

        @JsonIgnore
        public boolean hasActivity() {
            if ((this.spend != null && this.spend.signum() > 0)
                    || (this.impressions > 0)
                    || (this.clicks > 0)
                    || (this.platformFl > 0)
                    || (this.platformWl > 0)) {
                return true;
            }

            if (this.stageCells != null && !this.stageCells.isEmpty()) {
                for (StageCell cell : this.stageCells.values()) {
                    if (cell.getCount() > 0) return true;
                }
            }
            return false;
        }
    }
}
