package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.Getter;
import org.springframework.util.Assert;

@Getter
public final class DatePair implements Comparable<DatePair> {

    private final LocalDate first;
    private final LocalDate second;

    private DatePair(LocalDate first, LocalDate second) {
        Assert.notNull(first, "First must not be null");
        Assert.notNull(second, "Second must not be null");
        this.first = first;
        this.second = second;
    }

    public static DatePair of(LocalDate first, LocalDate second) {
        return new DatePair(first, second);
    }

    public static DatePair of(LocalDateTime first, LocalDateTime second) {
        return new DatePair(first.toLocalDate(), second.toLocalDate());
    }

    public static <V> DatePair findContainingDate(LocalDate dateToFind, NavigableMap<DatePair, V> datePairMap) {

        Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateToFind, dateToFind));

        if (entry != null && entry.getKey().contains(dateToFind)) return entry.getKey();

        return null;
    }

    private static LocalDate getEndDate(LocalDate startDate, TimePeriod timePeriod) {
        return switch (timePeriod) {
            case DAYS -> startDate;
            case WEEKS ->
                startDate.plus(1, timePeriod.getChronoUnit()).with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
            case MONTHS -> startDate.with(TemporalAdjusters.lastDayOfMonth());
            case QUARTERS -> startDate.plus(2, timePeriod.getChronoUnit()).with(TemporalAdjusters.lastDayOfMonth());
            case YEARS -> startDate.plus(1, timePeriod.getChronoUnit()).with(TemporalAdjusters.lastDayOfYear());
            default -> throw new IllegalStateException("Unexpected value: " + timePeriod);
        };
    }

    public <V> NavigableMap<DatePair, V> toTimePeriodMap(TimePeriod timePeriod, Supplier<V> valueSupplier) {
        Assert.notNull(timePeriod, "Time period must not be null");

        NavigableMap<DatePair, V> valueMap = new TreeMap<>();
        LocalDate current = this.first;

        while (!current.isAfter(this.second)) {
            LocalDate periodEnd = getEndDate(current, timePeriod);
            LocalDate actualEnd = periodEnd.isBefore(this.second) ? periodEnd : this.second;

            valueMap.put(DatePair.of(current, actualEnd), valueSupplier.get());

            current = periodEnd.plusDays(1);

            if (current.equals(periodEnd.plusDays(1)) && current.isAfter(this.second)) break;
        }

        return valueMap;
    }

    public boolean contains(LocalDate date) {
        return (date.isEqual(first) || date.isAfter(first)) && (date.isEqual(second) || date.isBefore(second));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DatePair datePair = (DatePair) o;

        return Objects.equals(first, datePair.first) && Objects.equals(second, datePair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.first, this.second);
    }

    @Override
    public String toString() {
        return String.format("%s->%s", first, second);
    }

    @Override
    public int compareTo(DatePair other) {
        if (other == null) return 1;

        int firstComparison = this.first.compareTo(other.first);
        return firstComparison != 0 ? firstComparison : this.second.compareTo(other.second);
    }
}
