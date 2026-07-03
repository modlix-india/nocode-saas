package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum AutonomyMode implements EnumType {
    RECOMMEND("RECOMMEND"),
    HYBRID("HYBRID"),
    AUTONOMOUS("AUTONOMOUS");

    private final String literal;

    AutonomyMode(String literal) {
        this.literal = literal;
    }

    public static AutonomyMode lookupLiteral(String literal) {
        return EnumType.lookupLiteral(AutonomyMode.class, literal);
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
