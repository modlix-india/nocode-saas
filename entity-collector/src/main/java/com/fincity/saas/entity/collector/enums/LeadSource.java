package com.fincity.saas.entity.collector.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LeadSource implements EnumType {
    WEBSITE("WEBSITE", "Website"),
    SOCIAL_MEDIA("SOCIAL_MEDIA", "Social Media");

    private final String literal;
    private final String name;

    LeadSource(String literal, String name) {
        this.literal = literal;
        this.name = name;
    }

    public static LeadSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LeadSource.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
