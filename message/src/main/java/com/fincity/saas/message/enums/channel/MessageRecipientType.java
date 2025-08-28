package com.fincity.saas.message.enums.channel;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum MessageRecipientType implements EnumType {
    PHONE_NUMBER("PHONE_NUMBER", "Phone Number");

    private final String literal;

    private final String displayName;

    MessageRecipientType(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static MessageRecipientType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessageRecipientType.class, literal);
    }

    @Override
    public String getLiteral() {
        return this.literal;
    }

    @Override
    public String getName() {
        return null;
    }
}
