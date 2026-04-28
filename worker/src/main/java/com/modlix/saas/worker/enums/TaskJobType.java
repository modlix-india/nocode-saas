package com.modlix.saas.worker.enums;

import org.jooq.EnumType;

public enum TaskJobType implements EnumType {
    SSL_RENEWAL("SSL_RENEWAL"),
    TOKEN_CLEANUP("TOKEN_CLEANUP"),
    PARTNER_DENORM_DELTA("PARTNER_DENORM_DELTA"),
    PARTNER_DENORM_FULL("PARTNER_DENORM_FULL");

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
