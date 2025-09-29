package com.fincity.saas.entity.processor.analytics.enums;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public enum TimePeriod implements TemporalUnit {
    NANOS("Nanos", 0, Duration.ofNanos(1), ChronoUnit.NANOS),
    MICROS("Micros", 1, Duration.ofNanos(1000), ChronoUnit.MICROS),
    MILLIS("Millis", 2, Duration.ofNanos(1000_000), ChronoUnit.MILLIS),
    SECONDS("Seconds", 3, Duration.ofSeconds(1), ChronoUnit.SECONDS),
    MINUTES("Minutes", 4, Duration.ofSeconds(60), ChronoUnit.MINUTES),
    HOURS("Hours", 5, Duration.ofSeconds(3600), ChronoUnit.HOURS),
    HALF_DAYS("HalfDays", 6, Duration.ofSeconds(43200), ChronoUnit.HALF_DAYS),
    DAYS("Days", 7, Duration.ofSeconds(86400), ChronoUnit.DAYS),
    WEEKS("Weeks", 8, Duration.ofSeconds(7 * 86400L), ChronoUnit.WEEKS),
    MONTHS("Months", 9, Duration.ofSeconds(31556952L / 12), ChronoUnit.MONTHS),
    QUARTERS("Quarters", 10, Duration.ofSeconds(31556952L / 3), ChronoUnit.MONTHS),
    YEARS("Years", 11, Duration.ofSeconds(31556952L), ChronoUnit.YEARS),
    DECADES("Decades", 12, Duration.ofSeconds(31556952L * 10L), ChronoUnit.DECADES),
    CENTURIES("Centuries", 13, Duration.ofSeconds(31556952L * 100L), ChronoUnit.CENTURIES),
    MILLENNIA("Millennia", 14, Duration.ofSeconds(31556952L * 1000L), ChronoUnit.MILLENNIA),
    ERAS("Eras", 15, Duration.ofSeconds(31556952L * 1000_000_000L), ChronoUnit.ERAS),
    FOREVER("Forever", 16, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), ChronoUnit.FOREVER);

    private static final Map<String, TimePeriod> BY_NAME = new HashMap<>();
    private static final Map<Integer, TimePeriod> BY_ID = new HashMap<>();

    static {
        for (TimePeriod timePeriod : values()) {
            BY_NAME.put(timePeriod.name, timePeriod);
            BY_ID.put(timePeriod.id, timePeriod);
        }
    }

    private final String name;
    private final Integer id;
    private final Duration duration;
    private final ChronoUnit chronoUnit;

    TimePeriod(String name, Integer id, Duration duration, ChronoUnit chronoUnit) {
        this.name = name;
        this.id = id;
        this.duration = duration;
        this.chronoUnit = chronoUnit;
    }

    public static TimePeriod getByName(String name) {
        return BY_NAME.get(StringUtils.capitalize(name.trim()));
    }

    public static TimePeriod getById(Integer id) {
        return BY_ID.get(id);
    }

    public static TimePeriod getByIdOrDefault(Integer id, TimePeriod defaultValue) {
        return BY_ID.getOrDefault(id, defaultValue);
    }

    public static List<TimePeriod> getTimePeriods() {
        return List.of(TimePeriod.values());
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public boolean isDurationEstimated() {
        return this.compareTo(DAYS) >= 0;
    }

    @Override
    public boolean isDateBased() {
        return this.compareTo(DAYS) >= 0 && this != FOREVER;
    }

    @Override
    public boolean isTimeBased() {
        return this.compareTo(DAYS) < 0;
    }

    @Override
    public boolean isSupportedBy(Temporal temporal) {
        return temporal.isSupported(this.chronoUnit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Temporal> R addTo(R temporal, long amount) {
        return (R) temporal.plus(amount, this.chronoUnit);
    }

    @Override
    public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
        return temporal1Inclusive.until(temporal2Exclusive, this.chronoUnit);
    }

    public long between(Temporal temporal1Inclusive, Temporal temporal2Inclusive, boolean endInclusive) {

        if (!endInclusive) return between(temporal1Inclusive, temporal2Inclusive);

        if (temporal1Inclusive.equals(temporal2Inclusive)) return 0;

        Temporal temporal2 = temporal2Inclusive.plus(1, this.chronoUnit);

        return temporal1Inclusive.until(temporal2, this.chronoUnit);
    }
}
