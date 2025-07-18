package com.fincity.saas.message.enums.call.provider.exotel;

import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.enums.call.ICallStatus;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ExotelCallStatus implements EnumType, ICallStatus {
    NULL("NULL", "null"),
    COMPLETED("COMPLETED", "completed"),
    BUSY("BUSY", "busy"),
    FAILED("FAILED", "failed"),
    NO_ANSWER("NO_ANSWER", "no-answer"),
    CANCELED("CANCELED", "canceled");

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

    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public CallStatus toCallStatus() {
        return switch (this) {
            case NULL -> CallStatus.ORIGINATE;
            case COMPLETED -> CallStatus.COMPLETE;
            case BUSY -> CallStatus.BUSY;
            case FAILED -> CallStatus.FAILED;
            case NO_ANSWER -> CallStatus.NO_ANSWER;
            case CANCELED -> CallStatus.CANCELED;
        };
    }
}
