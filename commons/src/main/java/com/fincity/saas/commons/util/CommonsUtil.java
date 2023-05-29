package com.fincity.saas.commons.util;

public class CommonsUtil {

	@SuppressWarnings("unchecked")
	public static <T> T nonNullValue(T... values) {

		for (T eachValue : values) {
			if (eachValue != null)
				return eachValue;
		}

		return null;
	}

	private CommonsUtil() {
	}
}
