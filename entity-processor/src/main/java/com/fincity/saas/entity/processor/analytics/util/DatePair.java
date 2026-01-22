package com.fincity.saas.entity.processor.analytics.util;

import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import java.io.Serial;
import java.io.Serializable;
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
public final class DatePair implements Comparable<DatePair>, Serializable {

	@Serial
	private static final long serialVersionUID = 4325130667466547523L;

	private final LocalDateTime first;
	private final LocalDateTime second;

	private DatePair(LocalDateTime first, LocalDateTime second) {
		Assert.notNull(first, "First must not be null");
		Assert.notNull(second, "Second must not be null");
		Assert.isTrue(!second.isBefore(first), "Second must not be before first");
		this.first = first;
		this.second = second;
	}

	public static DatePair of(LocalDateTime first, LocalDateTime second) {
		return new DatePair(first, second);
	}

	public static DatePair ofDates(LocalDate first, LocalDate second) {
		return new DatePair(first.atStartOfDay(), second.plusDays(1).atStartOfDay());
	}

	public static <V> DatePair findContaining(LocalDateTime dateTime, NavigableMap<DatePair, V> datePairMap) {
		Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateTime, LocalDateTime.MAX));
		return (entry != null && entry.getKey().contains(dateTime)) ? entry.getKey() : null;
	}

	public static <V> DatePair findContainingDate(LocalDate date, NavigableMap<DatePair, V> datePairMap) {
		LocalDateTime dateTime = date.atStartOfDay();
		Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateTime, LocalDateTime.MAX));
		return (entry != null && entry.getKey().containsDate(date)) ? entry.getKey() : null;
	}

	private static LocalDateTime getNextPeriodStart(LocalDateTime start, TimePeriod timePeriod) {
		LocalDate date = start.toLocalDate();
		return switch (timePeriod) {
			case DAYS -> date.plusDays(1).atStartOfDay();
			case WEEKS -> date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
			case MONTHS -> date.with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay();
			case QUARTERS -> date.plusMonths(3 - ((date.getMonthValue() - 1) % 3))
					.withDayOfMonth(1).atStartOfDay();
			case YEARS -> date.with(TemporalAdjusters.firstDayOfNextYear()).atStartOfDay();
			default -> throw new IllegalStateException("Unexpected value: " + timePeriod);
		};
	}

	public boolean contains(LocalDateTime dateTime) {
		return !dateTime.isBefore(first) && dateTime.isBefore(second);
	}

	public boolean containsDate(LocalDate date) {
		LocalDate firstDate = first.toLocalDate();
		LocalDate secondDate = second.toLocalDate();
		return !date.isBefore(firstDate) && date.isBefore(secondDate);
	}

	public <V> NavigableMap<DatePair, V> toTimePeriodMap(TimePeriod timePeriod, Supplier<V> valueSupplier) {
		Assert.notNull(timePeriod, "Time period must not be null");

		NavigableMap<DatePair, V> valueMap = new TreeMap<>();
		LocalDateTime current = this.first;

		while (current.isBefore(this.second)) {
			LocalDateTime periodEnd = getNextPeriodStart(current, timePeriod);
			LocalDateTime actualEnd = periodEnd.isBefore(this.second) ? periodEnd : this.second;

			valueMap.put(DatePair.of(current, actualEnd), valueSupplier.get());
			current = periodEnd;
		}

		return valueMap;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DatePair other = (DatePair) o;
		return first.equals(other.first) && second.equals(other.second);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

	@Override
	public String toString() {
		return String.format("[%s, %s)", first, second);
	}

	@Override
	public int compareTo(DatePair other) {
		if (other == null) return 1;
		int cmp = first.compareTo(other.first);
		return cmp != 0 ? cmp : second.compareTo(other.second);
	}
}
