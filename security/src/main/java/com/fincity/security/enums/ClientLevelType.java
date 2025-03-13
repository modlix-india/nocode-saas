package com.fincity.security.enums;

import com.fincity.security.jooq.enums.SecurityAppRegAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegFileAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegProfileLevel;

// Client level type with respect to the application.
// If the app is created by SYSTEM then everyone starts with CLIENT and then follows.
// If the app is created by a non SYSTEM client then their client is CLIENT and then follows.
public enum ClientLevelType {
    CONSUMER,
    CUSTOMER,
    CLIENT,
    OWNER;

    private static final String UNKNOWN_LEVEL = "Unknown level: ";

    public static ClientLevelType from(SecurityAppRegAccessLevel level) {
        return switch (level) {
            case CLIENT -> ClientLevelType.CLIENT;
            case CUSTOMER -> ClientLevelType.CUSTOMER;
            case CONSUMER -> ClientLevelType.CONSUMER;
            default -> throw new IllegalArgumentException(UNKNOWN_LEVEL + level);
        };
    }

    public static ClientLevelType from(SecurityAppRegProfileLevel level) {
        return switch (level) {
            case CLIENT -> ClientLevelType.CLIENT;
            case CUSTOMER -> ClientLevelType.CUSTOMER;
            case CONSUMER -> ClientLevelType.CONSUMER;
            default -> throw new IllegalArgumentException(UNKNOWN_LEVEL + level);
        };
    }

    public SecurityAppRegAccessLevel toAppAccessLevel() {
        return switch (this) {
            case CLIENT -> SecurityAppRegAccessLevel.CLIENT;
            case CUSTOMER -> SecurityAppRegAccessLevel.CUSTOMER;
            case CONSUMER -> SecurityAppRegAccessLevel.CONSUMER;
            default -> throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        };
    }

    public SecurityAppRegProfileLevel toProfileLevel() {
        return switch (this) {
            case CLIENT -> SecurityAppRegProfileLevel.CLIENT;
            case CUSTOMER -> SecurityAppRegProfileLevel.CUSTOMER;
            case CONSUMER -> SecurityAppRegProfileLevel.CONSUMER;
            default -> throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        };
    }

    public SecurityAppRegFileAccessLevel toFileAccessLevel() {
        return switch (this) {
            case CLIENT -> SecurityAppRegFileAccessLevel.CLIENT;
            case CUSTOMER -> SecurityAppRegFileAccessLevel.CUSTOMER;
            case CONSUMER -> SecurityAppRegFileAccessLevel.CONSUMER;
            default -> throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        };
    }

}
