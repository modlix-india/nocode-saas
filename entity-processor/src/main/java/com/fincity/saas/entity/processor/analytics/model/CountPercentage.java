package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.util.NumberUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CountPercentage implements Serializable, Comparable<CountPercentage> {

    @Serial
    private static final long serialVersionUID = 4023518798427014368L;

    private Number count;
    private Double percentage;

    public CountPercentage(CountPercentage countPercentage) {
        this.count = countPercentage.count;
        this.percentage = countPercentage.percentage;
    }

    public static CountPercentage zero() {
        return new CountPercentage().setCount(0L).setPercentage(0.0);
    }

    public static CountPercentage zeroNoPercent() {
        return new CountPercentage().setCount(0L);
    }

    public static CountPercentage of(Number count, Number wholePart, int roundPlaces) {
        Double percentage = NumberUtil.getPercentage(count, wholePart, roundPlaces);
        return new CountPercentage().setCount(count).setPercentage(percentage);
    }

    public static CountPercentage of(Number count, Number wholePart) {
        return of(count, wholePart, 2);
    }

    public static CountPercentage withCount(Number count) {
        return new CountPercentage().setCount(count);
    }

    public CountPercentage addCount(Number count) {
        this.count = NumberUtil.add(this.count, count);
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

    @Override
    public int compareTo(CountPercentage other) {
        if (other == null) return 1;
        if (this.count == null && other.count == null) return 0;
        if (this.count == null) return -1;
        if (other.count == null) return 1;
        return Long.compare(this.count.longValue(), other.count.longValue());
    }
}
