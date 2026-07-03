package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum ProvenanceSource implements EnumType {
    USER("USER"),
    AGENT("AGENT"),
    VERTICAL_DEFAULT("VERTICAL_DEFAULT"),
    ACCOUNT_DEFAULT("ACCOUNT_DEFAULT"),
    LOOP("LOOP"),
    IMPORT("IMPORT");

    private final String literal;

    ProvenanceSource(String literal) {
        this.literal = literal;
    }

    public static ProvenanceSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ProvenanceSource.class, literal);
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
