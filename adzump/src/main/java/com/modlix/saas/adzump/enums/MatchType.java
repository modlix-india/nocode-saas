package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum MatchType implements EnumType {
    BROAD("BROAD"),
    PHRASE("PHRASE"),
    EXACT("EXACT");

    private final String literal;

    MatchType(String literal) {
        this.literal = literal;
    }

    public static MatchType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MatchType.class, literal);
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
