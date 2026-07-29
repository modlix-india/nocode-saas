package com.modlix.saas.adzump.compile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Date/time formatting for platform payloads. The IR carries a {@link LocalDateTime} plus an IANA
 * timezone string (CONTRACT §1.1: schedule is expressed in the ad-account timezone). The compiler
 * anchors the local time to that zone and formats per platform.
 *
 * <ul>
 * <li><b>Meta</b> — ISO-8601 with offset, e.g. {@code 2026-07-01T00:00:00+05:30} (Graph API
 * {@code start_time}/{@code end_time}).</li>
 * <li><b>Google</b> — {@code yyyy-MM-dd} date only ({@code campaign.startDate}/{@code endDate}).</li>
 * </ul>
 *
 * <p>
 * A blank/unknown timezone is a hard failure, not a silent UTC default (the loop runs in the account
 * timezone; guessing it would corrupt scheduling).
 * </p>
 */
final class DateTimes {

    private static final DateTimeFormatter GOOGLE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateTimes() {
    }

    static String metaDateTime(LocalDateTime local, String timezone) {
        return zoned(local, timezone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static String googleDate(LocalDateTime local, String timezone) {
        return zoned(local, timezone).format(GOOGLE_DATE);
    }

    private static ZonedDateTime zoned(LocalDateTime local, String timezone) {
        if (local == null)
            throw new IllegalStateException("Schedule time is required for compilation");
        if (timezone == null || timezone.isBlank())
            throw new IllegalStateException("Schedule timezone is required for compilation (account tz)");
        return local.atZone(ZoneId.of(timezone.trim()));
    }
}
