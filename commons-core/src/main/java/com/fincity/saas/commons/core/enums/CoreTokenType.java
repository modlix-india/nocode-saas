package com.fincity.saas.commons.core.enums;

import org.jooq.EnumType;

public enum CoreTokenType implements EnumType {
    ACCESS("ACCESS"),

    REFRESH("REFRESH");

    private final String literal;

    CoreTokenType(String literal) {
        this.literal = literal;
    }

    public static CoreTokenType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CoreTokenType.class, literal);
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
