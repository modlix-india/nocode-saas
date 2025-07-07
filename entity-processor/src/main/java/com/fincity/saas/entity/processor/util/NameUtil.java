package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.util.StringUtil;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NameUtil {

    private NameUtil() {}

    public static String normalize(String str) {
        return StringUtil.toTitleCase(str.trim());
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
}
