package com.fincity.saas.message.enums.message.provider.whatsapp.cloud;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum MessageStatus implements EnumType {
    SENT("SENT", "sent"),
    DELIVERED("DELIVERED", "delivered"),
    READ("READ", "read"),
    FAILED("FAILED", "failed"),
    DELETED("DELETED", "deleted");

    private final String literal;
    private final String value;

    MessageStatus(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static MessageStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessageStatus.class, literal);
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
}
