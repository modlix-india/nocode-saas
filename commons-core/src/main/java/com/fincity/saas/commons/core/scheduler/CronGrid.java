package com.fincity.saas.commons.core.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

/**
 * Tick arithmetic on a cron grid.
 *
 * <p>
 * Every instance computes a tick from the cron and the zone alone, never from
 * "now" plus an interval, so two instances whose clocks disagree by a few
 * seconds still arrive at the same {@code tickAt} and the unique index on
 * schedule_run dedupes them.
 */
public final class CronGrid {

    private CronGrid() {
    }

    /**
     * Unix five-field cron gets Spring's seconds field prepended; six fields and
     * {@code @macros} are taken as they are.
     */
    public static CronExpression parse(String cron) {

        if (cron == null || cron.isBlank())
            throw new IllegalArgumentException("Cron is empty");

        String trimmed = cron.trim();
        if (!trimmed.startsWith("@") && trimmed.split("\\s+").length == 5)
            trimmed = "0 " + trimmed;

        return CronExpression.parse(trimmed);
    }

    /**
     * The latest tick at or before {@code now} and no older than {@code window},
     * or null when the grid has none in that span.
     *
     * <p>
     * Searches backwards by doubling, so a per-second cron costs a step or two
     * and a monthly one a dozen, rather than walking every tick in the window.
     */
    public static Instant latestTick(CronExpression cron, ZoneId zone, Instant now, Duration window) {

        ZonedDateTime nowZ = now.atZone(zone);
        ZonedDateTime floor = now.minus(window).atZone(zone);

        Duration back = Duration.ofSeconds(1);
        ZonedDateTime first = null;

        while (true) {
            ZonedDateTime from = nowZ.minus(back);
            if (from.isBefore(floor))
                from = floor;

            ZonedDateTime candidate = cron.next(from);
            if (candidate != null && !candidate.isAfter(nowZ)) {
                first = candidate;
                break;
            }

            if (!from.isAfter(floor))
                return null;

            back = back.multipliedBy(2);
        }

        ZonedDateTime latest = first;
        ZonedDateTime next = cron.next(latest);
        while (next != null && !next.isAfter(nowZ)) {
            latest = next;
            next = cron.next(latest);
        }

        return latest.toInstant();
    }

    /** A fixed instant the period of a cron is measured from, so every instance measures the same. */
    private static final Instant PERIOD_ANCHOR = Instant.parse("2026-01-05T00:00:00Z");

    /**
     * The latest tick of {@code key}'s shifted grid: every tick of the cron moved
     * later by a fixed offset in [0, period), where the period is the shortest
     * gap between consecutive ticks. The offset comes from the key alone, so two
     * instances agree on it and on the tick, and shifted ticks keep their order.
     */
    public static Instant latestSpreadTick(CronExpression cron, ZoneId zone, Instant now, Duration window,
            String key) {

        long offset = spreadOffsetSeconds(cron, zone, key);
        Instant tick = latestTick(cron, zone, now.minusSeconds(offset), window);
        return tick == null ? null : tick.plusSeconds(offset);
    }

    static long spreadOffsetSeconds(CronExpression cron, ZoneId zone, String key) {

        long period = shortestGapSeconds(cron, zone);
        if (period <= 1)
            return 0;
        // String.hashCode is specified by the JLS, so it is the same on every JVM.
        return Math.floorMod((long) key.hashCode(), period);
    }

    private static long shortestGapSeconds(CronExpression cron, ZoneId zone) {

        ZonedDateTime t = cron.next(PERIOD_ANCHOR.atZone(zone));
        long shortest = Long.MAX_VALUE;
        for (int i = 0; i < 16 && t != null; i++) {
            ZonedDateTime next = cron.next(t);
            if (next == null)
                break;
            shortest = Math.min(shortest, Duration.between(t, next).getSeconds());
            t = next;
        }
        return shortest == Long.MAX_VALUE ? 0 : shortest;
    }
}
