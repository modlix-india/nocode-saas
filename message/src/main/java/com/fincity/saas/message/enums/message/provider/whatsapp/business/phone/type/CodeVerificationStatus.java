package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum CodeVerificationStatus implements EnumType {
    VERIFIED("VERIFIED", "VERIFIED"),
    NOT_VERIFIED("NOT_VERIFIED", "NOT_VERIFIED"),
    EXPIRED("EXPIRED", "EXPIRED");

    private final String literal;
    private final String value;

    CodeVerificationStatus(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static CodeVerificationStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(CodeVerificationStatus.class, literal);
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
