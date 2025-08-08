package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ParameterType {
    TEXT("text"),
    CURRENCY("currency"),
    DATE_TIME("date_time"),
    IMAGE("image"),
    VIDEO("video"),
    DOCUMENT("document"),
    PAYLOAD("payload");

    private final String value;

    ParameterType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
