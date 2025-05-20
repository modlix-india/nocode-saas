package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.util.StringUtil;
import java.util.Locale;

public class NameUtil {

    private NameUtil() {}

    public static String normalize(String str) {
        return StringUtil.toTitleCase(str.trim());
    }

    public static String normalizeToUpper(String str) {
        return str.trim().toUpperCase(Locale.ROOT);
    }
}
