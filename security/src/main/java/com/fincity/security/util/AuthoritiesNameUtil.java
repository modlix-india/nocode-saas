package com.fincity.security.util;

public class AuthoritiesNameUtil {

    private AuthoritiesNameUtil() {
    }

    public static String makeName(String appCode, String name, boolean isRole) {
        StringBuilder sb = new StringBuilder("Authorities");

        if (appCode != null)
            sb.append(".").append(appCode.toUpperCase());

        sb.append(isRole ? ".ROLE_" : ".");
        sb.append(name.replace(' ', '_'));

        return sb.toString();
    }
}
