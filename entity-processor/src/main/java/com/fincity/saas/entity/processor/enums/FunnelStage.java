package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum FunnelStage implements EnumType {
    LEAD("LEAD", "Lead"),
    MQL("MQL", "Marketing Qualified Lead"),
    SQL("SQL", "Sales Qualified Lead"),
    WON("WON", "Won"),
    LOST("LOST", "Lost"),
    CUSTOM("CUSTOM", "Custom");

    private final String literal;
    private final String displayName;

    FunnelStage(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static FunnelStage lookupLiteral(String literal) {
        return EnumType.lookupLiteral(FunnelStage.class, literal);
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
