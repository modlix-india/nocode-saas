package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.util.StringUtil;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static boolean safeIsNotBlank(Object str) {
        return !StringUtil.safeIsBlank(str);
    }

    public static String assembleFullName(String firstName, String... names) {
        return Stream.concat(
                        Stream.ofNullable(firstName),
                        names == null ? Stream.empty() : Arrays.stream(names).filter(NameUtil::safeIsNotBlank))
                .map(String::trim)
                .filter(NameUtil::safeIsNotBlank)
                .collect(Collectors.joining(" "));
    }

    public static String assembleFullName(Object firstName, Object... names) {
        return Stream.concat(
                        Stream.ofNullable(firstName),
                        names == null ? Stream.empty() : Arrays.stream(names).filter(NameUtil::safeIsNotBlank))
                .map(Object::toString)
                .map(String::trim)
                .filter(NameUtil::safeIsNotBlank)
                .collect(Collectors.joining(" "));
    }

    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toLowerCase(Locale.ROOT) + str.substring(1);
    }
}
