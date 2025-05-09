package com.fincity.saas.entity.processor.enums;

import org.jooq.EnumType;

public enum StagePlatform implements EnumType {
    PRE_MAIN("PRE_MAIN"),
    MAIN("MAIN");

    private final String literal;

    StagePlatform(String literal) {
        this.literal = literal;
    }

    public static StagePlatform lookupLiteral(String literal) {
        return EnumType.lookupLiteral(StagePlatform.class, literal);
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
