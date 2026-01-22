package com.fincity.saas.entity.processor.analytics.util;

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

import org.springframework.util.Assert;

import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;

import lombok.Getter;

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

	public static <V> DatePair findContainingDate(LocalDate date, NavigableMap<DatePair, V> datePairMap) {
		LocalDateTime dateTime = date.atStartOfDay();
		Map.Entry<DatePair, V> entry = datePairMap.floorEntry(DatePair.of(dateTime, LocalDateTime.MAX));
		return (entry != null && entry.getKey().containsDate(date)) ? entry.getKey() : null;
	}

	private static LocalDateTime getPeriodEnd(LocalDateTime dateTime, TimePeriod timePeriod) {

		LocalDate date = dateTime.toLocalDate();

		return switch (timePeriod) {
			case WEEKS -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atStartOfDay();
			case MONTHS -> date.with(TemporalAdjusters.lastDayOfMonth()).atStartOfDay();
			case QUARTERS -> date.plusMonths(2 - ((date.getMonthValue() - 1) % 3))
					.with(TemporalAdjusters.lastDayOfMonth()).atStartOfDay();
			case YEARS -> date.with(TemporalAdjusters.lastDayOfYear()).atStartOfDay();
			default -> throw new IllegalStateException("Unexpected value: " + timePeriod);
		};
	}

	public boolean containsDate(LocalDate date) {
		LocalDate firstDate = first.toLocalDate();
		LocalDate secondDate = second.toLocalDate();
		return (date.isAfter(firstDate) || date.isEqual(firstDate))
				&& (date.isBefore(secondDate) || date.isEqual(secondDate));
	}

	public <V> NavigableMap<DatePair, V> toTimePeriodMap(TimePeriod timePeriod, Supplier<V> valueSupplier) {
		Assert.notNull(timePeriod, "Time period must not be null");

		NavigableMap<DatePair, V> valueMap = new TreeMap<>();
		LocalDateTime current = this.first;

		while (current.isBefore(this.second)) {
			LocalDateTime periodEnd = getPeriodEnd(current, timePeriod);
			LocalDateTime actualEnd = periodEnd.isBefore(this.second) ? periodEnd : this.second;

			valueMap.put(DatePair.of(current, actualEnd), valueSupplier.get());
			current = actualEnd.toLocalDate().plusDays(1).atStartOfDay();
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
		return String.format("[%s, %s]", first, second);
	}

	@Override
	public int compareTo(DatePair other) {
		if (other == null) return 1;
		int cmp = first.compareTo(other.first);
		return cmp != 0 ? cmp : second.compareTo(other.second);
	}
}
