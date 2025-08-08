package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum QualityRatingType implements EnumType {
    GREEN("GREEN", "GREEN"),
    YELLOW("YELLOW", "YELLOW"),
    RED("RED", "RED"),
    NA("NA", "NA"),
    UNKNOWN("UNKNOWN", "UNKNOWN");

    private final String literal;
    private final String value;

    QualityRatingType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static QualityRatingType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(QualityRatingType.class, literal);
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
