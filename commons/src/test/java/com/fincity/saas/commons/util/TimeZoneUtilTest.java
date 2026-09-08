package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule deciding which clock a person is on.
 *
 * <p>Worth testing directly because it is read from several services and none of them should be
 * reimplementing it. Two callers disagreeing about whether a blank override means "inherit" or
 * "UTC" is how a timestamp ends up displayed hours out with nothing obviously wrong anywhere: a
 * deal created at 13:35 showed as 07:05 PM for exactly that class of reason.
 */
class TimeZoneUtilTest {

    @Test
    @DisplayName("an override wins over the client default")
    void overrideWins() {
        assertEquals("Asia/Dubai", TimeZoneUtil.effective("Asia/Dubai", "Asia/Kolkata"));
    }

    @Test
    @DisplayName("no override falls through to the client default")
    void nullOverrideInherits() {
        // Null is the meaningful value on the user column and this is what makes it meaningful.
        assertEquals("Asia/Kolkata", TimeZoneUtil.effective(null, "Asia/Kolkata"));
        assertEquals("Asia/Kolkata", TimeZoneUtil.effective("", "Asia/Kolkata"));
        assertEquals("Asia/Kolkata", TimeZoneUtil.effective("   ", "Asia/Kolkata"));
    }

    @Test
    @DisplayName("an unusable override falls through rather than winning")
    void junkOverrideInherits() {
        // The direction matters. If a bad value won, one bad save would follow somebody around and
        // there would be no way to tell it from a deliberate choice.
        assertEquals("Asia/Kolkata", TimeZoneUtil.effective("Mars/Olympus_Mons", "Asia/Kolkata"));
    }

    @Test
    @DisplayName("both unusable yields null, not UTC")
    void bothUnusableIsNull() {
        // Never a silent UTC. UTC is nobody's working day, and a quiet substitution here would put
        // Indian business hours five and a half hours out while looking like it worked.
        assertNull(TimeZoneUtil.effective(null, null));
        assertNull(TimeZoneUtil.effective("nonsense", "also nonsense"));
    }

    @Test
    @DisplayName("sanitize never throws, unlike getZoneId")
    void sanitizeIsTotal() {
        // It sits on paths where the value came from a browser or an API caller. A zone name we
        // cannot read is a reason to fall back, not a reason to fail somebody's registration.
        assertNull(TimeZoneUtil.sanitize(null));
        assertNull(TimeZoneUtil.sanitize("Not/AZone"));
        assertEquals("Europe/London", TimeZoneUtil.sanitize("  Europe/London  "));
    }

    @Test
    @DisplayName("offset-style names are accepted and normalised")
    void offsetFormsSurvive() {
        // getZoneId already handled the UTC+HH:MM shape and sanitize routes through it, so a caller
        // sending an offset rather than a zone gets something usable instead of a null.
        assertEquals("UTC+05:30", TimeZoneUtil.sanitize("UTC+05:30"));
        assertEquals("UTC+05:30", TimeZoneUtil.sanitize("UTC+0530"));
        assertEquals("UTC-05:00", TimeZoneUtil.sanitize("UTC-5"));
    }

    @Test
    @DisplayName("tzdb aliases are kept as given, not rewritten")
    void aliasesAreLeftAlone() {
        // Browsers still report Asia/Calcutta in places and the phone-number mapper always does.
        // Both are links to Asia/Kolkata's rules, so behaviour is identical and normalising would
        // mean maintaining an alias table for a nicer-looking string.
        assertEquals("Asia/Calcutta", TimeZoneUtil.sanitize("Asia/Calcutta"));
        assertEquals("Asia/Kolkata", TimeZoneUtil.sanitize("Asia/Kolkata"));
    }
}
