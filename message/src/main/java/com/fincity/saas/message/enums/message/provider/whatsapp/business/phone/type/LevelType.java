package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LevelType implements EnumType {
    STANDARD("STANDARD", "STANDARD"),
    HIGH("HIGH", "HIGH"),
    NOT_APPLICABLE("NOT_APPLICABLE", "NOT_APPLICABLE");

    private final String literal;
    private final String value;

    LevelType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static LevelType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LevelType.class, literal);
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
