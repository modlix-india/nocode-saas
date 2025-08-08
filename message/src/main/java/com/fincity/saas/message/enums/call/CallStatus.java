package com.fincity.saas.message.enums.call;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum CallStatus implements EnumType {
    UNKNOWN("UNKNOWN", "Unknown"),
    QUEUED("QUEUED", "queued"),
    ORIGINATE("ORIGINATE", "Originate"),
    FAILED("FAILED", "Failed"),
    BUSY("BUSY", "Busy"),
    NO_ANSWER("NO_ANSWER", "No Answer"),
    COMPLETE("CALL_COMPLETE", "Call Complete"),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "Insufficient Balance"),
    CANCELED("CANCELED", "Call Canceled");

    private final String literal;
    private final String displayName;

    CallStatus(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static CallStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CallStatus.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
