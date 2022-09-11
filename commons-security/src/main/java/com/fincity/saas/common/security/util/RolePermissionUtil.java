package com.fincity.saas.common.security.util;

public class RolePermissionUtil {
	
	public static String toAuthorityString(String roleOrPermissionName) {
		return "Authorities." + roleOrPermissionName.replace(' ', '_');
	}

	private RolePermissionUtil() {
		
	}
}
