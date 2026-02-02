package com.fincity.saas.message.configuration.message.whatsapp;

import lombok.Getter;

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

    VERSION_23_0("v23.0"),

    LATEST("v23.0");

    private final String value;

    ApiVersion(String value) {
        this.value = value;
    }
}
