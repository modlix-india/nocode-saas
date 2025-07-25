package com.fincity.saas.message.model.message.whatsapp.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LevelType {
    STANDARD("STANDARD"),

    HIGH("HIGH"),
    NOT_APPLICABLE("NOT_APPLICABLE");

    private final String value;

    LevelType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
