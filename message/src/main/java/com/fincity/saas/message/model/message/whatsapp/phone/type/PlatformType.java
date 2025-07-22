package com.fincity.saas.message.model.message.whatsapp.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PlatformType {
    CLOUD_API("CLOUD_API"),

    ON_PREMISE("ON_PREMISE"),
    NOT_APPLICABLE("NOT_APPLICABLE");

    private final String value;

    PlatformType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
