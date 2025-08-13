package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EmailType {
    HOME("HOME"),
    WORK("WORK");

    private final String value;

    EmailType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
