package com.fincity.saas.message.configuration.message.whatsapp;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ApiVersion {

    UNVERSIONED(null),

    VERSION_16_0("v16.0"),

    VERSION_17_0("v17.0"),

    VERSION_18_0("v18.0"),

    VERSION_19_0("v19.0"),

    VERSION_20_0("v20.0"),

    VERSION_21_0("v21.0"),

    VERSION_22_0("v22.0"),

    LATEST("v22.0");

    private final String urlElement;

    ApiVersion(String urlElement) {
        this.urlElement = urlElement;
    }

    public static ApiVersion getVersionFromString(String urlElementStr) {
        if (urlElementStr == null)
            return UNVERSIONED;

        return Arrays.stream(ApiVersion.values())
                .filter(v -> urlElementStr.equals(v.getUrlElement()))
                .findFirst()
                .orElse(UNVERSIONED);
    }

    public boolean isUrlElementRequired() {
        return null != this.urlElement;
    }
}
