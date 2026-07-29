package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum CreativeAutonomy implements EnumType {
    APPROVED("APPROVED"),
    AUTONOMOUS("AUTONOMOUS");

    private final String literal;

    CreativeAutonomy(String literal) {
        this.literal = literal;
    }

    public static CreativeAutonomy lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CreativeAutonomy.class, literal);
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
