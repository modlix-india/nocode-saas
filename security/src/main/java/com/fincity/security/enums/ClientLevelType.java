package com.fincity.security.enums;

import com.fincity.security.jooq.enums.SecurityAppRegAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegFileAccessLevel;
import com.fincity.security.jooq.enums.SecurityAppRegPackageLevel;
import com.fincity.security.jooq.enums.SecurityAppRegUserRoleLevel;

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
        switch (level) {
            case CLIENT:
                return CLIENT;
            case CUSTOMER:
                return CUSTOMER;
            case CONSUMER:
                return CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + level);
        }
    }

    public static ClientLevelType from(SecurityAppRegPackageLevel level) {
        switch (level) {
            case CLIENT:
                return CLIENT;
            case CUSTOMER:
                return CUSTOMER;
            case CONSUMER:
                return CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + level);
        }
    }

    public static ClientLevelType from(SecurityAppRegUserRoleLevel level) {
        switch (level) {
            case CLIENT:
                return CLIENT;
            case CUSTOMER:
                return CUSTOMER;
            case CONSUMER:
                return CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + level);
        }
    }

    public SecurityAppRegAccessLevel toAppAccessLevel() {
        switch (this) {
            case CLIENT:
                return SecurityAppRegAccessLevel.CLIENT;
            case CUSTOMER:
                return SecurityAppRegAccessLevel.CUSTOMER;
            case CONSUMER:
                return SecurityAppRegAccessLevel.CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        }
    }

    public SecurityAppRegPackageLevel toPackageLevel() {
        switch (this) {
            case CLIENT:
                return SecurityAppRegPackageLevel.CLIENT;
            case CUSTOMER:
                return SecurityAppRegPackageLevel.CUSTOMER;
            case CONSUMER:
                return SecurityAppRegPackageLevel.CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        }
    }

    public SecurityAppRegUserRoleLevel toUserRoleLevel() {
        switch (this) {
            case CLIENT:
                return SecurityAppRegUserRoleLevel.CLIENT;
            case CUSTOMER:
                return SecurityAppRegUserRoleLevel.CUSTOMER;
            case CONSUMER:
                return SecurityAppRegUserRoleLevel.CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        }
    }

    public SecurityAppRegFileAccessLevel toFileAccessLevel() {
        switch (this) {
            case CLIENT:
                return SecurityAppRegFileAccessLevel.CLIENT;
            case CUSTOMER:
                return SecurityAppRegFileAccessLevel.CUSTOMER;
            case CONSUMER:
                return SecurityAppRegFileAccessLevel.CONSUMER;
            default:
                throw new IllegalArgumentException(UNKNOWN_LEVEL + this);
        }
    }

}
