package com.modlix.saas.commons2.util;

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
}
