package com.fincity.saas.entity.collector.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LeadSource implements EnumType {

    WEBSITE("WEBSITE"),
    SOCIAL_MEDIA("SOCIAL_MEDIA");

    private final String literal;

    LeadSource(String literal) {
        this.literal = literal;
    }

    public static LeadSource lookupLiteral(String literal) {
       return EnumType.lookupLiteral(LeadSource.class,literal);
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
