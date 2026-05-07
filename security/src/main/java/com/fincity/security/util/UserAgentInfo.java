package com.fincity.security.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

public final class UserAgentInfo {

    public static final int MAX_UA_LENGTH = 512;

    public static final String DEVICE_MOBILE = "MOBILE";
    public static final String DEVICE_TABLET = "TABLET";
    public static final String DEVICE_DESKTOP = "DESKTOP";
    public static final String DEVICE_BOT = "BOT";
    public static final String DEVICE_UNKNOWN = "UNKNOWN";

    // Order matters in browser detection: Edge/Opera/Samsung/Chrome must precede Safari,
    // because Chromium-based browsers all carry "Safari" in their UA strings.
    private static final Pattern EDGE = Pattern.compile("Edg(?:e|A|iOS)?/(\\d+)");
    private static final Pattern OPERA = Pattern.compile("(?:OPR|Opera)/(\\d+)");
    private static final Pattern SAMSUNG = Pattern.compile("SamsungBrowser/(\\d+)");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/(\\d+)");
    private static final Pattern CHROME = Pattern.compile("(?:Chrome|CriOS)/(\\d+)");
    private static final Pattern SAFARI = Pattern.compile("Version/(\\d+).*Safari/");
    private static final Pattern IE = Pattern.compile("(?:MSIE |Trident/.*rv:)(\\d+)");

    private static final Pattern WIN = Pattern.compile("Windows NT (\\d+\\.\\d+)");
    private static final Pattern MAC = Pattern.compile("Mac OS X (\\d+(?:[._]\\d+)?)");
    private static final Pattern IOS = Pattern.compile("OS (\\d+(?:_\\d+)?) like Mac");
    private static final Pattern ANDROID = Pattern.compile("Android (\\d+(?:\\.\\d+)?)");

    private static final Pattern BOT = Pattern.compile(
            "(?i)bot|crawler|spider|slurp|bingpreview|facebookexternalhit|curl/|wget/|python-requests|okhttp/|java/|httpclient");

    private final String raw;
    private final String deviceType;
    private final String os;
    private final String browser;

    private UserAgentInfo(String raw, String deviceType, String os, String browser) {
        this.raw = raw;
        this.deviceType = deviceType;
        this.os = os;
        this.browser = browser;
    }

    public static UserAgentInfo from(ServerHttpRequest request) {
        if (request == null)
            return empty();
        return parse(request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
    }

    public static UserAgentInfo parse(String ua) {
        if (ua == null || ua.isBlank())
            return empty();

        String trimmed = ua.length() > MAX_UA_LENGTH ? ua.substring(0, MAX_UA_LENGTH) : ua;

        if (BOT.matcher(trimmed).find())
            return new UserAgentInfo(trimmed, DEVICE_BOT, detectOs(trimmed), detectBrowser(trimmed));

        return new UserAgentInfo(trimmed, detectDevice(trimmed), detectOs(trimmed), detectBrowser(trimmed));
    }

    private static UserAgentInfo empty() {
        return new UserAgentInfo(null, null, null, null);
    }

    private static String detectDevice(String ua) {
        // Tablet checks first — iPad and Android tablets contain "Mobile" markers we want to override.
        if (ua.contains("iPad") || (ua.contains("Android") && !ua.contains("Mobile")) || ua.contains("Tablet"))
            return DEVICE_TABLET;
        if (ua.contains("Mobile") || ua.contains("iPhone") || ua.contains("iPod") || ua.contains("Android"))
            return DEVICE_MOBILE;
        return DEVICE_DESKTOP;
    }

    private static String detectOs(String ua) {
        Matcher m = WIN.matcher(ua);
        if (m.find())
            return "Windows " + windowsName(m.group(1));
        m = IOS.matcher(ua);
        if (m.find())
            return "iOS " + m.group(1).replace('_', '.');
        m = MAC.matcher(ua);
        if (m.find())
            return "macOS " + m.group(1).replace('_', '.');
        m = ANDROID.matcher(ua);
        if (m.find())
            return "Android " + m.group(1);
        if (ua.contains("CrOS"))
            return "ChromeOS";
        if (ua.contains("Linux"))
            return "Linux";
        return null;
    }

    private static String detectBrowser(String ua) {
        Matcher m = EDGE.matcher(ua);
        if (m.find())
            return "Edge " + m.group(1);
        m = OPERA.matcher(ua);
        if (m.find())
            return "Opera " + m.group(1);
        m = SAMSUNG.matcher(ua);
        if (m.find())
            return "Samsung " + m.group(1);
        m = FIREFOX.matcher(ua);
        if (m.find())
            return "Firefox " + m.group(1);
        m = CHROME.matcher(ua);
        if (m.find())
            return "Chrome " + m.group(1);
        m = SAFARI.matcher(ua);
        if (m.find())
            return "Safari " + m.group(1);
        m = IE.matcher(ua);
        if (m.find())
            return "IE " + m.group(1);
        return null;
    }

    private static String windowsName(String nt) {
        return switch (nt) {
            case "10.0" -> "10/11";
            case "6.3" -> "8.1";
            case "6.2" -> "8";
            case "6.1" -> "7";
            default -> nt;
        };
    }

    public String raw() {
        return raw;
    }

    public String deviceType() {
        return deviceType;
    }

    public String os() {
        return os;
    }

    public String browser() {
        return browser;
    }
}
