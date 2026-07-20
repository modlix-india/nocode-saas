package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum Cadence implements EnumType {
    DAILY("DAILY"),
    TWICE_DAILY("TWICE_DAILY"),
    HOURLY("HOURLY"),
    ON_DEMAND("ON_DEMAND");

    private final String literal;

    Cadence(String literal) {
        this.literal = literal;
    }

    public static Cadence lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Cadence.class, literal);
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
