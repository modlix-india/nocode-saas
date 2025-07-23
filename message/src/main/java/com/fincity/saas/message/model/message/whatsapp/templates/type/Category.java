package com.fincity.saas.message.model.message.whatsapp.templates.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Category {
    AUTHENTICATION("AUTHENTICATION"),
    UTILITY("UTILITY"),
    MARKETING("MARKETING");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
