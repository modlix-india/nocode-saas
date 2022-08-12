package com.fincity.security.util;

import org.jooq.types.ULong;

public class ULongUtil {

	public static ULong valueOf(Object o) {

		if (o == null)
			return null;

		if (o instanceof ULong v)
			return v;

		return ULong.valueOf(o.toString());
	}

	private ULongUtil() {
	}
}
