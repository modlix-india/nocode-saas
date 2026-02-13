package com.fincity.sass.worker.enums;

import org.jooq.EnumType;

public enum TaskLastFireStatus implements EnumType {
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String literal;

    TaskLastFireStatus(String literal) {
        this.literal = literal;
    }

    public static TaskLastFireStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TaskLastFireStatus.class, literal);
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
