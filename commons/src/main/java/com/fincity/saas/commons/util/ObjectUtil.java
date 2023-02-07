package com.fincity.saas.commons.util;

public class ObjectUtil {
	
	public static boolean safeEquals(Object a, Object b) {
		
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	private ObjectUtil() {
	}
}
