package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum MessagingLimitTier implements EnumType {
    TIER_50("TIER_50", "TIER_50"),
    TIER_250("TIER_250", "TIER_250"),
    TIER_1K("TIER_1K", "TIER_1K"),
    TIER_10K("TIER_10K", "TIER_10K"),
    TIER_100K("TIER_100K", "TIER_100K"),
    TIER_UNLIMITED("TIER_UNLIMITED", "TIER_UNLIMITED");

    private final String literal;
    private final String value;

    MessagingLimitTier(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static MessagingLimitTier lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessagingLimitTier.class, literal);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }
}
