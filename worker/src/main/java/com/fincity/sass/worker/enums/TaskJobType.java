package com.fincity.sass.worker.enums;

import org.jooq.EnumType;

public enum TaskJobType implements EnumType {
    SIMPLE("SIMPLE"),
    CRON("CRON"),
    SSL_RENEWAL("SSL_RENEWAL"),
    TICKET_EXPIRATION("TICKET_EXPIRATION");

    private final String literal;

    TaskJobType(String literal) {
        this.literal = literal;
    }

    public static TaskJobType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TaskJobType.class, literal);
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
