package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum SpecialAdCategory implements EnumType {
    HOUSING("HOUSING"),
    EMPLOYMENT("EMPLOYMENT"),
    CREDIT("CREDIT"),
    FINANCIAL("FINANCIAL"),
    NONE("NONE");

    private final String literal;

    SpecialAdCategory(String literal) {
        this.literal = literal;
    }

    public static SpecialAdCategory lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SpecialAdCategory.class, literal);
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
