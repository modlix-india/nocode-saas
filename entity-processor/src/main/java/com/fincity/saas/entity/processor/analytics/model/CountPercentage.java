package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.util.NumberUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CountPercentage {

    private Number count;
    private Double percentage;

    private CountPercentage(Number count, Double percentage) {
        this.count = count;
        this.percentage = percentage;
    }

    public static CountPercentage zero() {
        return new CountPercentage(0L, 0.0);
    }

    public static CountPercentage zeroNoPercent() {
        return new CountPercentage(0L, null);
    }

    public static CountPercentage of(Number count, Number wholePart, int roundPlaces) {
        Double percentage = NumberUtil.getPercentage(count, wholePart, roundPlaces);
        return new CountPercentage(count, percentage);
    }

    public static CountPercentage of(Number count, Number wholePart) {
        return of(count, wholePart, 2);
    }

    public static CountPercentage withCount(Number count) {
        return new CountPercentage(count, null);
    }

    public CountPercentage addCount(Number count) {
        this.count = this.count.longValue() + count.longValue();
        return this;
    }

    public CountPercentage recalculatePercentage(Number wholePart) {
        return this.recalculatePercentage(wholePart, 2);
    }

    public CountPercentage recalculatePercentage(Number wholePart, int roundPlaces) {
        this.percentage = NumberUtil.getPercentage(this.count, wholePart, roundPlaces);
        return this;
    }

    public CountPercentage add(CountPercentage other) {
        this.count = NumberUtil.add(this.count, other.count);
        this.percentage = this.percentage + other.percentage;
        return this;
    }

    public CountPercentage incrementCount() {
        this.count = this.count.longValue() + 1;
        return this;
    }
}
