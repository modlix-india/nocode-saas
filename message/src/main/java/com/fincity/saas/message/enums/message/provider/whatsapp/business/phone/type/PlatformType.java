package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum PlatformType implements EnumType {
    CLOUD_API("CLOUD_API", "CLOUD_API"),
    ON_PREMISE("ON_PREMISE", "ON_PREMISE"),
    NOT_APPLICABLE("NOT_APPLICABLE", "NOT_APPLICABLE");

    private final String literal;
    private final String value;

    PlatformType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PlatformType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(PlatformType.class, literal);
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
