package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum HeaderType {
    TEXT("text"),
    IMAGE("image");
    private final String value;

    HeaderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
