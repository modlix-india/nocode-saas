package com.fincity.saas.commons.util;

public class StringUtil {

	public static String safeValueOf(Object obj, String... defaultValue) {

		if (obj == null) {
			for (String s : defaultValue) {
				if (s == null)
					continue;
				return s;
			}

			return null;
		}

		return obj.toString();
	}

	private StringUtil() {
	}
}
