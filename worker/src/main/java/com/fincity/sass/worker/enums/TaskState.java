package com.fincity.sass.worker.enums;

import org.jooq.EnumType;

public enum TaskState implements EnumType {
    NONE("NONE"),
    NORMAL("NORMAL"),
    PAUSED("PAUSED"),
    COMPLETE("COMPLETE"),
    ERROR("ERROR"),
    BLOCKED("BLOCKED");

    private final String literal;

    TaskState(String literal) {
        this.literal = literal;
    }

    public static TaskState lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TaskState.class, literal);
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
