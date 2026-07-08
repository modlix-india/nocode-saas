package com.modlix.saas.adzump.enums;

import org.jooq.EnumType;

public enum CampaignType implements EnumType {
    SEARCH("SEARCH"),
    PMAX("PMAX"),
    DEMAND_GEN("DEMAND_GEN"),
    DISPLAY("DISPLAY"),
    VIDEO("VIDEO"),
    APP("APP"),
    SHOPPING("SHOPPING"),
    DSA("DSA"),
    LEADS("LEADS"),
    SALES("SALES"),
    TRAFFIC("TRAFFIC"),
    AWARENESS("AWARENESS"),
    ENGAGEMENT("ENGAGEMENT"),
    ADVANTAGE_PLUS("ADVANTAGE_PLUS");

    private final String literal;

    CampaignType(String literal) {
        this.literal = literal;
    }

    public static CampaignType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CampaignType.class, literal);
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
