package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class CronGridTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final Duration DAY = Duration.ofHours(25);

    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void fiveFieldCronIsReadAsUnixCron() {
        CronExpression cron = CronGrid.parse("*/15 * * * *");
        assertEquals(at("2026-10-09T09:45:00Z"), CronGrid.latestTick(cron, UTC, at("2026-10-09T09:52:31Z"), DAY));
    }

    @Test
    void sixFieldCronAndMacrosAreTakenAsTheyAre() {
        assertEquals(at("2026-10-09T09:52:30Z"), CronGrid.latestTick(CronGrid.parse("*/10 * * * * *"), UTC,
                at("2026-10-09T09:52:31Z"), DAY));
        assertEquals(at("2026-10-09T09:00:00Z"), CronGrid.latestTick(CronGrid.parse("@hourly"), UTC,
                at("2026-10-09T09:52:31Z"), DAY));
        assertThrows(IllegalArgumentException.class, () -> CronGrid.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> CronGrid.parse("not a cron"));
    }

    @Test
    void aTickExactlyNowIsTheLatest() {
        assertEquals(at("2026-10-09T09:00:00Z"), CronGrid.latestTick(CronGrid.parse("0 * * * *"), UTC,
                at("2026-10-09T09:00:00Z"), DAY));
    }

    @Test
    void instancesSecondsApartLandOnTheSameGridTick() {
        CronExpression cron = CronGrid.parse("* * * * *");
        Instant early = CronGrid.latestTick(cron, UTC, at("2026-10-09T09:05:00.200Z"), DAY);
        Instant late = CronGrid.latestTick(cron, UTC, at("2026-10-09T09:05:47Z"), DAY);
        assertEquals(at("2026-10-09T09:05:00Z"), early);
        assertEquals(early, late);
    }

    @Test
    void theCronIsReadInItsZone() {
        // 09:00 in Kolkata is 03:30 UTC.
        assertEquals(at("2026-10-09T03:30:00Z"), CronGrid.latestTick(CronGrid.parse("0 9 * * *"),
                ZoneId.of("Asia/Kolkata"), at("2026-10-09T05:00:00Z"), DAY));
    }

    @Test
    void sparseCronsAreFoundAcrossTheWholeWindow() {
        assertEquals(at("2026-10-01T00:00:00Z"), CronGrid.latestTick(CronGrid.parse("0 0 1 * *"), UTC,
                at("2026-10-09T09:00:00Z"), Duration.ofDays(31)));
        // Older than the window: nothing to catch up.
        assertNull(CronGrid.latestTick(CronGrid.parse("0 0 1 * *"), UTC, at("2026-10-09T09:00:00Z"), DAY));
    }
}
