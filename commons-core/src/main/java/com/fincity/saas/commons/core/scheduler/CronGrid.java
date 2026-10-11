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
}
