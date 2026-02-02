package com.fincity.saas.entity.processor.enums.content;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ContentEntitySeries implements EnumType {
    OWNER("OWNER", "Owner"),
    TICKET("TICKET", "Ticket"),
    USER("USER", "User");

    private final String literal;
    private final String displayName;

    ContentEntitySeries(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static ContentEntitySeries lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ContentEntitySeries.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
