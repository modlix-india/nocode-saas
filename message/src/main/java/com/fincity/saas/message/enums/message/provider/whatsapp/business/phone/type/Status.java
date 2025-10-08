package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum Status implements EnumType {
    PENDING("PENDING", "PENDING"),
    DELETED("DELETED", "DELETED"),
    MIGRATED("MIGRATED", "MIGRATED"),
    BANNED("BANNED", "BANNED"),
    RESTRICTED("RESTRICTED", "RESTRICTED"),
    RATE_LIMITED("RATE_LIMITED", "RATE_LIMITED"),
    FLAGGED("FLAGGED", "FLAGGED"),
    CONNECTED("CONNECTED", "CONNECTED"),
    DISCONNECTED("DISCONNECTED", "DISCONNECTED"),
    UNKNOWN("UNKNOWN", "UNKNOWN"),
    UNVERIFIED("UNVERIFIED", "UNVERIFIED");

    private final String literal;
    private final String value;

    Status(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static Status lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Status.class, literal);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }
}
