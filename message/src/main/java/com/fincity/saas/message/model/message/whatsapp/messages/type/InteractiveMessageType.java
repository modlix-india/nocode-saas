package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum InteractiveMessageType {
    BUTTON("button"), //
    LIST("list"), //
    PRODUCT("product"), //
    PRODUCT_LIST("product_list");

    private final String value;

    InteractiveMessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
