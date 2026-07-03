package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum PlatformObjective implements EnumType {
    LEADS("LEADS"),
    CONVERSIONS("CONVERSIONS"),
    TRAFFIC("TRAFFIC"),
    AWARENESS("AWARENESS"),
    ENGAGEMENT("ENGAGEMENT"),
    SALES("SALES"),
    APP("APP");

    private final String literal;

    PlatformObjective(String literal) {
        this.literal = literal;
    }

    public static PlatformObjective lookupLiteral(String literal) {
        return EnumType.lookupLiteral(PlatformObjective.class, literal);
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
