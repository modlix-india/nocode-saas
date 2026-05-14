package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ConversionEventStatus implements EnumType {
    PENDING("PENDING", "Pending"),
    SENT("SENT", "Sent"),
    FAILED("FAILED", "Failed"),
    SKIPPED("SKIPPED", "Skipped");

    private final String literal;
    private final String displayName;

    ConversionEventStatus(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static ConversionEventStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ConversionEventStatus.class, literal);
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
