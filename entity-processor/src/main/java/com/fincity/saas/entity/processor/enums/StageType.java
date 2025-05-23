package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum StageType implements EnumType {
    OPEN("OPEN", "Open", false),
    CLOSED("CLOSED", "Closed", true);

    private final String literal;
    private final String displayName;
    private final boolean hasSuccessFailure;

    StageType(String literal, String displayName, boolean hasSuccessFailure) {
        this.literal = literal;
        this.displayName = displayName;
        this.hasSuccessFailure = hasSuccessFailure;
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
