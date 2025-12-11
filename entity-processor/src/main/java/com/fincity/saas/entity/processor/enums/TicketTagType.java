package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum TicketTagType implements EnumType {
    HOT("HOT", "Hot"),
    WARM("WARM", "Warm"),
    COLD("COLD", "Cold");

    private final String literal;
    private final String displayName;

    TicketTagType(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static TicketTagType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TicketTagType.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return displayName;
    }
}
