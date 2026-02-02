package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.util.StringUtil;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NameUtil {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Cc}]");

    private static final Set<String> LOWERCASE_WORDS =
            Set.of("a", "an", "the", "and", "but", "or", "nor", "at", "by", "for", "in", "of", "on", "to", "up", "via");

    private NameUtil() {}

    public static String normalize(String str) {
        if (str == null || str.isEmpty()) return str;

        String cleaned = CONTROL_CHARS
                .matcher(MULTIPLE_SPACES.matcher(str.strip()).replaceAll(" "))
                .replaceAll("");

        if (cleaned.length() <= 4 && cleaned.matches("[A-Z0-9]+")) return cleaned;

        return applyTitleCase(cleaned);
    }

    private static String applyTitleCase(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        int start = 0;
        int wordIndex = 0;

        for (int i = 0; i <= str.length(); i++) {
            if (i == str.length() || str.charAt(i) == ' ') {
                if (i > start) {
                    appendWord(sb, str.substring(start, i), wordIndex, i < str.length());
                    wordIndex++;
                }
                start = i + 1;
            }
        }

        return sb.toString();
    }

    private static void appendWord(StringBuilder sb, String word, int wordIndex, boolean addSpace) {
        String lower = word.toLowerCase();

        if (wordIndex == 0 || !LOWERCASE_WORDS.contains(lower)) {
            sb.append(Character.toUpperCase(lower.charAt(0))).append(lower, 1, lower.length());
        } else {
            sb.append(lower);
        }

        if (addSpace) sb.append(' ');
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
