package com.fincity.saas.message.model.message.whatsapp.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NameStatusType {
    APPROVED("APPROVED"),

    AVAILABLE_WITHOUT_REVIEW("AVAILABLE_WITHOUT_REVIEW"),

    DECLINED("DECLINED"),

    EXPIRED("EXPIRED"),

    PENDING_REVIEW("PENDING_REVIEW"),

    NONE("NONE");

    private final String value;

    NameStatusType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
