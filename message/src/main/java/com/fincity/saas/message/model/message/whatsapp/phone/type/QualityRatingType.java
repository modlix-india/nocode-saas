package com.fincity.saas.message.model.message.whatsapp.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum QualityRatingType {
    GREEN("GREEN"),

    YELLOW("YELLOW"),

    RED("RED"),

    NA("NA"),
    UNKNOWN("UNKNOWN");

    private final String value;

    QualityRatingType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
