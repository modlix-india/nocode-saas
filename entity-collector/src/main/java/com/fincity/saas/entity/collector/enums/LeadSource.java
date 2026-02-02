package com.fincity.saas.entity.collector.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LeadSource implements EnumType {
    WEBSITE("WEBSITE", "Website"),
    SOCIAL_MEDIA("SOCIAL_MEDIA", "Social Media");

    private final String literal;
    private final String value;

    LeadSource(String literal, String name) {
        this.literal = literal;
        this.value = name;
    }

    public static LeadSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LeadSource.class, literal);
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
