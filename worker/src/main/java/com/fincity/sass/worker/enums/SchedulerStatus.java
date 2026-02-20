package com.fincity.sass.worker.enums;

import org.jooq.EnumType;

public enum SchedulerStatus implements EnumType {
    STARTED("STARTED"),
    STANDBY("STANDBY"),
    SHUTDOWN("SHUTDOWN");

    private final String literal;

    SchedulerStatus(String literal) {
        this.literal = literal;
    }

    public static SchedulerStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SchedulerStatus.class, literal);
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
