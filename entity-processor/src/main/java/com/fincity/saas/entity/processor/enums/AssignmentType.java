package com.fincity.saas.entity.processor.enums;

import org.jooq.EnumType;

public enum AssignmentType implements EnumType {
    MANUAL("MANUAL","Manual"),
    DEAL_FLOW("DEAL_FLOW","Deal_Flow");

    private final String literal;
    private final String displayName;

    AssignmentType(String literal ,String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static AssignmentType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(AssignmentType.class, literal);
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
