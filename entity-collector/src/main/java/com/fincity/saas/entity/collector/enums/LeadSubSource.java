package com.fincity.saas.entity.collector.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LeadSubSource implements EnumType {
    WEBSITE_FORM("WEBSITE_FORM"),
    FACEBOOK("FACEBOOK");

    private final String literal;

    LeadSubSource(String literal) {
        this.literal = literal;
    }

    public static LeadSubSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LeadSubSource.class, literal);
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
