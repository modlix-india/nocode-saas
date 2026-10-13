package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.model.ScheduleModel;

/**
 * The builder's conversions. A schedule a person sets in their own zone has to
 * land on the UTC grid the scheduler reads, and come back looking the same.
 */
class ScheduleConverterTest {

    private static final int IST = 330;
    private static final int NEPAL = 345;
    private static final int NEW_YORK_WINTER = -300;

    private static ScheduleModel base() {
        return new ScheduleModel().setFrequency("off").setEvery(5).setMinute(0).setHour(9)
                .setDays(List.of(1, 2, 3, 4, 5)).setDayOfMonth("1").setCron("");
    }

    private static ScheduleModel minutes(int every) {
        return base().setFrequency("minutes").setEvery(every);
    }

    private static ScheduleModel hours(int every, int start, int minute) {
        return base().setFrequency("hours").setEvery(every).setHour(start).setMinute(minute);
    }

    private static ScheduleModel weekly(int hour, int minute, Integer... days) {
        return base().setFrequency("weekly").setHour(hour).setMinute(minute).setDays(List.of(days));
    }

    private static ScheduleModel monthly(String day, int hour, int minute) {
        return base().setFrequency("monthly").setDayOfMonth(day).setHour(hour).setMinute(minute);
    }

    // ---------------------------------------------------------------- to cron

    @Test
    void minuteStepsDoNotDependOnTheZone() {
        assertEquals("* * * * *", ScheduleConverter.toUtcCron(minutes(1), IST));
        assertEquals("*/15 * * * *", ScheduleConverter.toUtcCron(minutes(15), IST));
    }

    @Test
    void everyHourMovesOnlyTheMinute() {
        assertEquals("30 * * * *", ScheduleConverter.toUtcCron(hours(1, 0, 0), IST));
        assertEquals("25 * * * *", ScheduleConverter.toUtcCron(hours(1, 0, 10), NEPAL));
    }

    @Test
    void everyNHoursListsTheUtcHours() {
        assertEquals("30 0,6,12,18 * * *", ScheduleConverter.toUtcCron(hours(6, 0, 0), IST));
    }

    @Test
    void weekdayMorningInIst() {
        assertEquals("30 3 * * 1,2,3,4,5", ScheduleConverter.toUtcCron(weekly(9, 0, 1, 2, 3, 4, 5), IST));
    }

    @Test
    void crossingMidnightMovesTheWeekdays() {
        // 02:00 Monday IST is 20:30 Sunday UTC; 21:00 Saturday New York (winter) is 02:00 Sunday UTC.
        assertEquals("30 20 * * 0", ScheduleConverter.toUtcCron(weekly(2, 0, 1), IST));
        assertEquals("0 2 * * 0", ScheduleConverter.toUtcCron(weekly(21, 0, 6), NEW_YORK_WINTER));
    }

    @Test
    void everyDayIsAStar() {
        assertEquals("15 9 * * *", ScheduleConverter.toUtcCron(weekly(9, 15, 0, 1, 2, 3, 4, 5, 6), 0));
    }

    @Test
    void monthlyDaysShiftAndTheFirstCanBecomeTheLastDay() {
        assertEquals("30 3 15 * *", ScheduleConverter.toUtcCron(monthly("15", 9, 0), IST));
        assertEquals("30 20 L * *", ScheduleConverter.toUtcCron(monthly("1", 2, 0), IST));
        assertEquals("30 20 L-1 * *", ScheduleConverter.toUtcCron(monthly("L", 2, 0), IST));
    }

    @Test
    void offIsEmptyAndCustomIsPassedThrough() {
        assertEquals("", ScheduleConverter.toUtcCron(base(), IST));
        assertEquals("0 5-55/5 * * * ?",
                ScheduleConverter.toUtcCron(base().setFrequency("custom").setCron(" 0 5-55/5 * * * ? "), IST));
    }

    // ---------------------------------------------------------------- round trips

    private record Case(ScheduleModel model, int offset) {
    }

    @Test
    void roundTrips() {
        List<Case> cases = List.of(
                new Case(minutes(5), IST),
                new Case(hours(1, 0, 10), IST),
                new Case(hours(6, 2, 45), IST),
                new Case(weekly(9, 0, 1, 2, 3, 4, 5), IST),
                new Case(weekly(2, 0, 1), IST),
                new Case(weekly(21, 0, 6), NEW_YORK_WINTER),
                new Case(weekly(0, 5, 0, 1, 2, 3, 4, 5, 6), NEPAL),
                new Case(monthly("1", 2, 0), IST),
                new Case(monthly("L", 2, 0), IST),
                new Case(monthly("28", 23, 0), NEW_YORK_WINTER));

        for (Case c : cases) {
            String cron = ScheduleConverter.toUtcCron(c.model(), c.offset());
            assertEquals(c.model(), ScheduleConverter.fromCron(cron, c.offset()), cron);
        }
    }

    // ---------------------------------------------------------------- from cron

    @Test
    void readsTheCronsStoredToday() {
        assertEquals(minutes(1), ScheduleConverter.fromCron("0 * * * * ?", IST));
        assertEquals(hours(1, 0, 40), ScheduleConverter.fromCron("0 10 * * * ?", IST));
        assertEquals(weekly(8, 50, 0, 1, 2, 3, 4, 5, 6), ScheduleConverter.fromCron("0 20 3 * * ?", IST));
        assertEquals(base().setFrequency("custom").setCron("0 5-55/5 * * * ?"),
                ScheduleConverter.fromCron("0 5-55/5 * * * ?", IST));
    }

    @Test
    void macrosAndNames() {
        assertEquals(weekly(0, 0, 0, 1, 2, 3, 4, 5, 6), ScheduleConverter.fromCron("@daily", 0));
        assertEquals(weekly(9, 0, 1, 2, 3, 4, 5), ScheduleConverter.fromCron("0 9 * * MON-FRI", 0));
    }

    @Test
    void shapesTheBuilderCannotDrawStayCustom() {
        for (String cron : List.of("30 0 9 * * *", "0 9 1 1 *", "0 9 1 * 1", "*/7 * * * *", "0 1,5 * * *",
                "nonsense"))
            assertEquals("custom", ScheduleConverter.fromCron(cron, IST).getFrequency(), cron);
    }

    @Test
    void nothingStoredIsOff() {
        assertEquals("off", ScheduleConverter.fromCron(null, IST).getFrequency());
        assertEquals("off", ScheduleConverter.fromCron("  ", IST).getFrequency());
    }

    // ---------------------------------------------------------------- reading

    @Test
    void nextRunsAreShownInTheDisplayZone() {
        Instant from = Instant.parse("2026-10-12T10:07:30Z");
        assertEquals(List.of("Tue 13 Oct 2026, 09:00", "Wed 14 Oct 2026, 09:00", "Thu 15 Oct 2026, 09:00"),
                ScheduleConverter.nextRuns("30 3 * * 1-5", ZoneId.of("UTC"), ZoneId.of("Asia/Kolkata"), from, 3));
    }

    @Test
    void aCronTheSchedulerCannotReadIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleConverter.parse("61 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleConverter.parse(""));
    }

    @Test
    void daylightSaving() {
        assertFalse(ScheduleConverter.observesDst(ZoneId.of("Asia/Kolkata"), 2026));
        assertTrue(ScheduleConverter.observesDst(ZoneId.of("Europe/London"), 2026));
    }

    @Test
    void describesInWords() {
        assertEquals("Weekdays at 09:00 (Asia/Kolkata)",
                ScheduleConverter.describe(weekly(9, 0, 1, 2, 3, 4, 5), "Asia/Kolkata"));
        assertEquals("Every minute", ScheduleConverter.describe(minutes(1), "UTC"));
        assertEquals("Last day of every month at 02:00 (UTC)", ScheduleConverter.describe(monthly("L", 2, 0), "UTC"));
    }
}
