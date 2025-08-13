package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ButtonSubType {
    QUICK_REPLY("quick_reply"),
    URL("url");

    private final String value;

    ButtonSubType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
