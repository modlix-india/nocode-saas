package com.fincity.saas.entity.collector.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LeadSubSource implements EnumType {
    WEBSITE_FORM("WEBSITE_FORM", "Website Form"),
    FACEBOOK("FACEBOOK", "Facebook"),
    GOOGLE( "GOOGLE", "Google");

    private final String literal;
    private final String value;

    LeadSubSource(String literal, String name) {
        this.literal = literal;
        this.value = name;
    }

    public static LeadSubSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LeadSubSource.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }

    @JsonValue
    public String getValue() {   // Jackson serializes this as the JSON value
        return value;
    }
}
