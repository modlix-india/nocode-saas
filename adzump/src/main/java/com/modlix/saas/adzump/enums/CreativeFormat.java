package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum CreativeFormat implements EnumType {
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    CAROUSEL("CAROUSEL"),
    RSA("RSA"),
    DEMAND_GEN("DEMAND_GEN");

    private final String literal;

    CreativeFormat(String literal) {
        this.literal = literal;
    }

    public static CreativeFormat lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CreativeFormat.class, literal);
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
