package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import org.jooq.EnumType;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum ParameterFormat implements EnumType {
    NAMED("NAMED", "NAMED"),
    POSITIONAL("POSITIONAL", "POSITIONAL");

    private final String literal;
    private final String value;

    ParameterFormat(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static ParameterFormat lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ParameterFormat.class, literal);
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
