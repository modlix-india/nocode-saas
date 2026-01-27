package com.fincity.saas.entity.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import java.io.Serial;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Getter;
import org.springframework.util.Assert;

@Getter
public final class DatePair implements Comparable<DatePair>, Serializable {

    @Serial
    private static final long serialVersionUID = 4325130667466547523L;

    private static final Map<String, ZoneId> ZONE_CACHE = new ConcurrentHashMap<>();

    private static final LocalDateTime MAX_DATE_TIME = LocalDateTime.MAX;

    private final LocalDateTime first;
    private final LocalDateTime second;
    private final String timezone;

    @JsonIgnore
    private final transient ZoneId zoneId;

    private DatePair(LocalDateTime first, LocalDateTime second, String timezone) {
        Assert.notNull(first, "First must not be null");
        Assert.notNull(second, "Second must not be null");
        Assert.isTrue(!second.isBefore(first), "Second must not be before first");
        this.first = first;
        this.second = second;
        this.timezone = timezone;
        this.zoneId = resolveZoneId(timezone);
    }

    public static DatePair of(LocalDateTime first, LocalDateTime second) {
        return new DatePair(first, second, null);
    }

    public static DatePair of(LocalDateTime start, LocalDateTime end, String timezone) {
        return new DatePair(start, end, timezone);
    }

    public static ZoneId resolveZoneId(String timezone) {
        if (StringUtil.safeIsBlank(timezone)) return ZoneOffset.UTC;

        return ZONE_CACHE.computeIfAbsent(timezone, k -> {
            try {
                return ZoneId.of(k);
            } catch (DateTimeException e) {
                return ZoneOffset.UTC;
            }
        });
    }

    public static LocalDateTime convertToUtc(LocalDateTime dateTime, String timezone) {
        if (StringUtil.safeIsBlank(timezone)) return dateTime;

        ZoneId zoneId = resolveZoneId(timezone);
        return dateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime convertEpochSecondsToUtc(long epochSeconds, String timezone) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);

        if (StringUtil.safeIsBlank(timezone)) return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        ZoneId zoneId = resolveZoneId(timezone);
        return instant.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static <V> DatePair findContainingDate(LocalDate date, NavigableMap<DatePair, V> datePairMap) {
        if (date == null || datePairMap == null || datePairMap.isEmpty()) return null;

        LocalDateTime dateTime = date.atStartOfDay();
        Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateTime, MAX_DATE_TIME));
        return (entry != null && entry.getKey().containsDate(date)) ? entry.getKey() : null;
    }

    public static <V> DatePair findContainingDate(
            LocalDateTime utcDateTime, NavigableMap<DatePair, V> datePairMap, String timezone) {
        if (utcDateTime == null || datePairMap == null || datePairMap.isEmpty()) return null;

        LocalDateTime dateTimeToUse = convertToTimezone(utcDateTime, timezone);
        Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateTimeToUse, MAX_DATE_TIME));
        return (entry != null && entry.getKey().contains(dateTimeToUse)) ? entry.getKey() : null;
    }

    private static LocalDateTime convertToTimezone(LocalDateTime utcDateTime, String timezone) {
        if (StringUtil.safeIsBlank(timezone)) return utcDateTime;

        try {
            ZoneId zoneId = resolveZoneId(timezone);
            return utcDateTime
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(zoneId)
                    .toLocalDateTime();
        } catch (Exception e) {
            return utcDateTime;
        }
    }

    private static LocalDateTime getPeriodEnd(LocalDate date, TimePeriod timePeriod) {
        return switch (timePeriod) {
            case DAYS -> date.plusDays(1).atStartOfDay();
            case WEEKS -> date.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atStartOfDay();
            case MONTHS -> getMonthEnd(date);
            case QUARTERS -> getQuarterEnd(date);
            case YEARS -> getYearEnd(date);
            default -> throw new IllegalArgumentException("Unexpected value: " + timePeriod);
        };
    }

    private static LocalDateTime getMonthEnd(LocalDate date) {
        LocalDate lastDay = date.with(TemporalAdjusters.lastDayOfMonth());
        return (lastDay.isAfter(date) ? lastDay : date.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()))
                .atStartOfDay();
    }

    private static LocalDateTime getQuarterEnd(LocalDate date) {
        int monthInQuarter = (date.getMonthValue() - 1) % 3;
        LocalDate quarterEnd = date.plusMonths(2L - monthInQuarter).with(TemporalAdjusters.lastDayOfMonth());
        if (quarterEnd.isAfter(date)) {
            return quarterEnd.atStartOfDay();
        }
        LocalDate nextQuarterStart = date.plusMonths(3);
        int nextMonthInQuarter = (nextQuarterStart.getMonthValue() - 1) % 3;
        return nextQuarterStart
                .plusMonths(2L - nextMonthInQuarter)
                .with(TemporalAdjusters.lastDayOfMonth())
                .atStartOfDay();
    }

    private static LocalDateTime getYearEnd(LocalDate date) {
        LocalDate lastDay = date.with(TemporalAdjusters.lastDayOfYear());
        return (lastDay.isAfter(date) ? lastDay : date.plusYears(1).with(TemporalAdjusters.lastDayOfYear()))
                .atStartOfDay();
    }

    public boolean containsDate(LocalDate date) {
        LocalDate firstDate = first.toLocalDate();
        LocalDate secondDate = second.toLocalDate();
        return !date.isBefore(firstDate) && !date.isAfter(secondDate);
    }

    public boolean contains(LocalDateTime dateTime) {
        return !dateTime.isBefore(first) && !dateTime.isAfter(second);
    }

    public <V> NavigableMap<DatePair, V> toTimePeriodMap(TimePeriod timePeriod, Supplier<V> valueSupplier) {
        Assert.notNull(timePeriod, "Time period must not be null");

        NavigableMap<DatePair, V> valueMap = new TreeMap<>();
        LocalDateTime current = this.first;

        while (current.isBefore(this.second)) {
            LocalDateTime periodEnd = getPeriodEnd(current.toLocalDate(), timePeriod);
            LocalDateTime actualEnd = periodEnd.isBefore(this.second) ? periodEnd : this.second;

            valueMap.put(DatePair.of(current, actualEnd), valueSupplier.get());
            current = actualEnd.toLocalDate().plusDays(1).atStartOfDay();
        }

        return valueMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DatePair other)) return false;
        return first.equals(other.first) && second.equals(other.second) && Objects.equals(timezone, other.timezone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, timezone);
    }

    @Override
    public String toString() {
        return timezone != null
                ? "DatePair[first=%s, second=%s, timezone=%s]".formatted(first, second, timezone)
                : "DatePair[first=%s, second=%s]".formatted(first, second);
    }

    @Override
    public int compareTo(DatePair other) {
        if (other == null) return 1;
        int cmp = first.compareTo(other.first);
        return cmp != 0 ? cmp : second.compareTo(other.second);
    }
}
