package com.fincity.saas.commons.util;

import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeZoneUtil {

    private static final Pattern UTC_PATTERN = Pattern.compile("UTC([+-]?)(\\d{1,2}):?(\\d{2})?");

    private TimeZoneUtil() {
    }

    public static ZoneId getZoneId(String zoneName) {
        if (zoneName.toUpperCase().startsWith("UTC")) {
            Matcher matcher = UTC_PATTERN.matcher(zoneName);
            if (matcher.matches()) {
                String sign = matcher.group(1);
                int hours = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                int minutes = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                // System.out.println(sign + " - " + hours + " - " + minutes);
                zoneName = "UTC" + (sign == null || sign.equals("-") || sign.equals("") ? "-" : "+")
                        + String.format("%02d", hours)
                        + String.format("%02d", minutes);
            }
        }

        return ZoneId.of(zoneName);
    }

    /**
     * The canonical form of a zone name, or null when it says nothing usable.
     *
     * <p>Unlike {@link #getZoneId}, this never throws. It sits on paths where the value arrived from
     * a browser or an API caller, and a zone name we cannot read is a reason to fall back to a
     * default, not a reason to fail a registration.
     *
     * <p>Null and blank both mean "no opinion" and are returned as null, so callers can tell that
     * apart from a zone that was supplied and rejected only by checking the input.
     */
    public static String sanitize(String zoneName) {

        if (zoneName == null || zoneName.isBlank()) return null;

        try {
            return getZoneId(zoneName.trim()).getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The zone that actually applies, given an override and the default it overrides.
     *
     * <p>One place, deliberately. This rule is read by anything that wants to know what clock a
     * person is on, and two callers disagreeing about whether blank means "inherit" or "UTC" is
     * exactly how a timestamp ends up displayed hours out with nothing obviously wrong anywhere.
     *
     * <p>An unusable override falls through to the default rather than winning, so a bad value
     * saved once cannot follow somebody around.
     *
     * @return the effective zone name, or null when neither says anything
     */
    public static String effective(String override, String fallback) {

        String chosen = sanitize(override);

        return chosen != null ? chosen : sanitize(fallback);
    }
}
