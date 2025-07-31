package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum Category implements EnumType {
    AUTHENTICATION("AUTHENTICATION", "AUTHENTICATION"),
    UTILITY("UTILITY", "UTILITY"),
    MARKETING("MARKETING", "MARKETING");

    private final String literal;
    private final String value;

    Category(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static Category lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Category.class, literal);
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
