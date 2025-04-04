package com.fincity.security.enums;

// Client level type with respect to the application.
// If the app is created by SYSTEM then everyone starts with CLIENT and then follows.
// If the app is created by a non SYSTEM client then their client is CLIENT and then follows.
public enum ClientLevelType {
    CONSUMER,
    CUSTOMER,
    CLIENT,
    OWNER;

    public static ClientLevelType from(Object level) {
        return ClientLevelType.valueOf(level.toString());
    }

    public <T extends Enum<T>> T to(Class<T> type) {
        return Enum.valueOf(type, this.toString());
    }

}
