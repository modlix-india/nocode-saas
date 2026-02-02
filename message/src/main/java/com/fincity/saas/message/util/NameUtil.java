package com.fincity.saas.message.util;

import com.fincity.saas.commons.util.StringUtil;
import java.util.Locale;
import java.util.regex.Pattern;

public class NameUtil {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Cc}]");

    private NameUtil() {}

    public static String normalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String result = str.strip();
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");
        result = CONTROL_CHARS.matcher(result).replaceAll("");
        return StringUtil.toTitleCase(result);
    }

    public static String normalizeToUpper(String str) {
        return str.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean containsUrl(String text) {
        return text.contains("http://") || text.contains("https://");
    }
}
