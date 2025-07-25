package com.fincity.saas.message.model.message.whatsapp.webhook.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatus {
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed"),
    DELETED("deleted");

    private final String value;

    MessageStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
