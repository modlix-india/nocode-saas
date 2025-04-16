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

	public static boolean onlyAlphabetAllowed(String appCode) {

		if (appCode == null)
			return false;

		for (int i = 0; i < appCode.length(); i++) {
			char c = appCode.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
				continue;

			return false;
		}

		return true;
	}

	public static String removeLineFeedOrNewLineChars(String str) {

		if (str == null)
			return str;

		int len = str.length() - 1;

		while (len >= 0 && (str.charAt(len) == '\r' || str.charAt(len) == '\n'))
			len--;

		return str.substring(0, len + 1);
	}

	public static String removeSpecialCharacters(String str) {

		if (str == null || str.isBlank())
			return str;

		StringBuilder sb = new StringBuilder(str.length());

		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (Character.isLetter(c))
				sb.append(c);
		}

		return sb.toString();
	}

	public static String capitalize(String str) {

		if (str == null || str.isBlank())
			return str;

		char base = Character.toUpperCase(str.charAt(0));

		char[] chars = str.toCharArray();
		chars[0] = base;

		return new String(chars);

	}
}
