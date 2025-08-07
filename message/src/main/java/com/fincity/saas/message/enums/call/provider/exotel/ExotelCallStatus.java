package com.fincity.saas.message.enums.call.provider.exotel;

import org.jooq.EnumType;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.enums.call.ICallStatus;

import lombok.Getter;

@Getter
public enum ExotelCallStatus implements EnumType, ICallStatus {
    QUEUED("QUEUED", "queued"),
    IN_PROGRESS("IN_PROGRESS", "in-progress"),
    COMPLETED("COMPLETED", "completed"),
    FAILED("FAILED", "failed"),
    BUSY("BUSY", "busy"),
    NO_ANSWER("NO_ANSWER", "no-answer");

    private final String literal;
    private final String displayName;

    ExotelCallStatus(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static ExotelCallStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ExotelCallStatus.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public CallStatus toCallStatus() {
        return switch (this) {
            case QUEUED -> CallStatus.QUEUED;
            case IN_PROGRESS -> CallStatus.ORIGINATE;
            case COMPLETED -> CallStatus.COMPLETE;
            case FAILED -> CallStatus.FAILED;
            case BUSY -> CallStatus.BUSY;
            case NO_ANSWER -> CallStatus.NO_ANSWER;
        };
    }
}
