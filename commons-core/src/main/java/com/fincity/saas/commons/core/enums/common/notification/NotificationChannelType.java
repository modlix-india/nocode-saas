package com.fincity.saas.commons.core.enums.common.notification;

import org.jooq.EnumType;

public enum NotificationChannelType implements EnumType {
    DISABLED("DISABLED"),
    EMAIL("EMAIL"),
    IN_APP("IN_APP"),
    MOBILE_PUSH("MOBILE_PUSH"),
    WEB_PUSH("WEB_PUSH"),
    SMS("SMS");

    private final String literal;

    NotificationChannelType(String literal) {
        this.literal = literal;
    }

    public static NotificationChannelType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(NotificationChannelType.class, literal);
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
