package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ComponentType {
    BODY("body"),
    HEADER("header"),
    BUTTON("button");

    private final String value;

    ComponentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
