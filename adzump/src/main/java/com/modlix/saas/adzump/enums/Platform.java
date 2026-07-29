package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum Platform implements EnumType {
    META("META"),
    GOOGLE("GOOGLE");

    private final String literal;

    Platform(String literal) {
        this.literal = literal;
    }

    public static Platform lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Platform.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }
}
