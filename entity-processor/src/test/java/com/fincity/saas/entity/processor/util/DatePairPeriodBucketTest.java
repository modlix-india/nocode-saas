package com.fincity.saas.entity.processor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.NavigableMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bucket boundaries every time-series report is drawn on.
 *
 * <p>Nothing covered these before, and the daily case was wrong the whole time: a bucket ran to the
 * end of the <em>following</em> day, so a week of data came back as four rows, each labelled with
 * the first of the two days it had quietly merged. A chart like that does not look broken — it
 * looks like a business with half as many days in it — which is exactly why it wants a test rather
 * than an eye.
 */
class DatePairPeriodBucketTest {

    private static final String IST = "Asia/Kolkata";

    private static NavigableMap<DatePair, String> buckets(LocalDate from, LocalDate to, TimePeriod period) {
        return DatePair.of(from.atStartOfDay(), to.atTime(LocalTime.MAX), IST).toTimePeriodMap(period, () -> "");
    }

    @Test
    @DisplayName("a day bucket covers one day, and a seven day range is seven of them")
    void dailyBucketsAreOneDayEach() {

        NavigableMap<DatePair, String> rows =
                buckets(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 21), TimePeriod.DAYS);

        assertEquals(7, rows.size(), "seven calendar days must produce seven buckets");

        rows.keySet()
                .forEach(bucket -> assertEquals(
                        bucket.getFirst().toLocalDate(),
                        bucket.getSecond().toLocalDate(),
                        "a daily bucket must open and close on the same date"));

        assertEquals(LocalDate.of(2026, 8, 15), rows.firstKey().getFirst().toLocalDate());
        assertEquals(LocalDate.of(2026, 8, 21), rows.lastKey().getFirst().toLocalDate());
    }

    @Test
    @DisplayName("weeks run Monday to Sunday, matching how the DAO groups them in SQL")
    void weeklyBucketsRunMondayToSunday() {

        // Opens on a Monday so every bucket is a whole week.
        NavigableMap<DatePair, String> rows =
                buckets(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 9, 13), TimePeriod.WEEKS);

        assertEquals(4, rows.size());
        rows.keySet().forEach(bucket -> {
            assertEquals(
                    java.time.DayOfWeek.MONDAY,
                    bucket.getFirst().getDayOfWeek(),
                    "PeriodBucketUtil groups on MySQL WEEKDAY(), which counts Monday as 0");
            assertEquals(java.time.DayOfWeek.SUNDAY, bucket.getSecond().getDayOfWeek());
        });
    }

    @Test
    @DisplayName("a range opening mid-week keeps the partial week as its own bucket")
    void partialOpeningWeekIsItsOwnBucket() {

        // Wednesday. The DAO will stamp these rows with Monday the 17th, which is earlier
        // than any bucket here - CampaignReportService clamps that into the first bucket
        // rather than dropping it. This pins the shape that clamping depends on.
        NavigableMap<DatePair, String> rows =
                buckets(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 9, 13), TimePeriod.WEEKS);

        DatePair first = rows.firstKey();
        assertEquals(LocalDate.of(2026, 8, 19), first.getFirst().toLocalDate());
        assertEquals(LocalDate.of(2026, 8, 23), first.getSecond().toLocalDate(), "clipped to that week's Sunday");
        assertFalse(
                first.contains(LocalDate.of(2026, 8, 17).atStartOfDay()),
                "the week's true Monday sits outside the bucket, which is why the service clamps");
    }

    @Test
    @DisplayName("buckets never overlap and never leave a gap")
    void bucketsTileTheRange() {

        for (TimePeriod period :
                new TimePeriod[] {TimePeriod.DAYS, TimePeriod.WEEKS, TimePeriod.MONTHS, TimePeriod.QUARTERS}) {

            NavigableMap<DatePair, String> rows =
                    buckets(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), period);

            DatePair previous = null;
            for (DatePair bucket : rows.keySet()) {
                if (previous != null) {
                    assertEquals(
                            previous.getSecond().toLocalDate().plusDays(1),
                            bucket.getFirst().toLocalDate(),
                            period + " buckets must be contiguous");
                }
                previous = bucket;
            }

            assertEquals(LocalDate.of(2026, 1, 1), rows.firstKey().getFirst().toLocalDate());
            assertEquals(
                    LocalDate.of(2026, 12, 31), rows.lastKey().getSecond().toLocalDate());
        }
    }
}
