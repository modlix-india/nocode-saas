package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum CodeMethodType implements EnumType {
    SMS("SMS", "SMS"),
    VOICE("VOICE", "VOICE");

    private final String literal;
    private final String value;

    CodeMethodType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static CodeMethodType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CodeMethodType.class, literal);
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
