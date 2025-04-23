package com.fincity.security.util;

public class AuthoritiesNameUtil {

    private AuthoritiesNameUtil() {
    }

    public static String makeRoleName(String appCode, String name) {
        StringBuilder sb = new StringBuilder("Authorities.");

        if (appCode != null)
            sb.append(appCode.toUpperCase()).append(".");

        sb.append("ROLE_").append(name.replace(' ', '_'));

        return sb.toString();
    }

    public static String makeProfileName(String appCode, String name) {
        StringBuilder sb = new StringBuilder("Authorities.");

        if (appCode != null)
            sb.append(appCode.toUpperCase()).append(".");

        sb.append("PROFILE_").append(name.replace(' ', '_'));

        return sb.toString();
    }

    public static String makePermissionName(String appCode, String name) {
        StringBuilder sb = new StringBuilder("Authorities.");

        if (appCode != null)
            sb.append(appCode.toUpperCase()).append(".");

        sb.append(name.replace(' ', '_'));

        return sb.toString();
    }
}
