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

	public static boolean safeIsBlank(Object object) {

		return object == null || object.toString()
		        .isBlank();
	}

	public static boolean safeEquals(String str, String str2) {

		if (str == str2) // NOSONAR
			// This is a valid check if the instances are same.
			return true;

		if (str == null || str2 == null)
			return false;

		return str.equals(str2);
	}
}
