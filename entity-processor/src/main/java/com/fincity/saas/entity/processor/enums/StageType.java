package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum StageType implements EnumType {
    OPEN("OPEN", "Open"),
    CLOSED("CLOSED", "Closed");

    private final String literal;
    private final String displayName;

    StageType(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static StageType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(StageType.class, literal);
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
