package com.fincity.saas.message.enums.message.provider.whatsapp.cloud;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum MessageType implements EnumType {
    AUDIO("AUDIO", "audio"),
    BUTTON("BUTTON", "button"),
    CONTACTS("CONTACTS", "contacts"),
    DOCUMENT("DOCUMENT", "document"),
    LOCATION("LOCATION", "location"),
    TEXT("TEXT", "text"),
    TEMPLATE("TEMPLATE", "template"),
    IMAGE("IMAGE", "image"),
    INTERACTIVE("INTERACTIVE", "interactive"),
    ORDER("ORDER", "order"),
    REACTION("REACTION", "reaction"),
    STICKER("STICKER", "sticker"),
    SYSTEM("SYSTEM", "system"),
    UNKNOWN("UNKNOWN", "unknown"),
    VIDEO("VIDEO", "video"),
    UNSUPPORTED("UNSUPPORTED", "unsupported");

    private final String literal;
    private final String value;

    MessageType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static MessageType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessageType.class, literal);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }

    public boolean isMediaFile() {
        return this.equals(AUDIO)
                || this.equals(DOCUMENT)
                || this.equals(IMAGE)
                || this.equals(STICKER)
                || this.equals(VIDEO);
    }
}
