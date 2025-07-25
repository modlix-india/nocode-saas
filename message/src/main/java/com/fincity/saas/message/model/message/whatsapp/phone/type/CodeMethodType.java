package com.fincity.saas.message.model.message.whatsapp.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CodeMethodType {
    SMS("SMS"),

    VOICE("VOICE");

    private final String value;

    CodeMethodType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
