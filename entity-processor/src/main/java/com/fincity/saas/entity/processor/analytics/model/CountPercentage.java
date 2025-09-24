package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.util.NumberUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CountPercentage {

    private Number counts;
    private Double percentage;

    private CountPercentage(Number counts, Double percentage) {
        this.counts = counts;
        this.percentage = percentage;
    }

    public static CountPercentage zero() {
        return new CountPercentage(0L, 0.0);
    }

    public static CountPercentage of(Number counts, Number wholePart) {
        return of(counts, wholePart, 2);
    }

    public static CountPercentage of(Number counts, Number wholePart, int roundPlaces) {
        Double percentage = NumberUtil.getPercentage(counts, wholePart, roundPlaces);
        return new CountPercentage(counts, percentage);
    }

    public static CountPercentage withCount(Number counts) {
        return new CountPercentage(counts, 0.0);
    }

    public static CountPercentage withPercentage(Double percentage) {
        return new CountPercentage(0L, percentage);
    }

    public CountPercentage addCount(Number counts) {
        this.counts = this.counts.longValue() + counts.longValue();
        return this;
    }

    public CountPercentage recalculatePercentage(Number wholePart) {
        return recalculatePercentage(wholePart, 2);
    }

    public CountPercentage recalculatePercentage(Number wholePart, int roundPlaces) {
        this.percentage = NumberUtil.getPercentage(this.counts, wholePart, roundPlaces);
        return this;
    }

    public CountPercentage add(CountPercentage other) {
        this.counts = this.counts.longValue() + other.counts.longValue();
        this.percentage = this.percentage + other.percentage;
        return this;
    }

    public CountPercentage incrementCount() {
        this.counts = this.counts.longValue() + 1;
        return this;
    }
}
