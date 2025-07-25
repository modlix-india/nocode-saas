package com.fincity.saas.message.model.message.whatsapp.messages.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageType {
    AUDIO("audio"),
    BUTTON("button"),
    CONTACTS("contacts"),
    DOCUMENT("document"),
    LOCATION("location"),
    TEXT("text"),
    TEMPLATE("template"),
    IMAGE("image"),
    INTERACTIVE("interactive"),
    ORDER("order"),
    REACTION("reaction"),
    STICKER("sticker"),
    SYSTEM("system"),
    UNKNOWN("unknown"),
    VIDEO("video"),
    UNSUPPORTED("unsupported");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
