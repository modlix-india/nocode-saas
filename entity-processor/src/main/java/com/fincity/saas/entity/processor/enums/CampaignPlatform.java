package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum CampaignPlatform implements EnumType {
    GOOGLE("GOOGLE"),
    FACEBOOK("FACEBOOK"),
    LINKEDIN("LINKEDIN"),
    X("X");

    private final String literal;

    CampaignPlatform(String literal) {
        this.literal = literal;
    }

    public static CampaignPlatform lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CampaignPlatform.class, literal);
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
