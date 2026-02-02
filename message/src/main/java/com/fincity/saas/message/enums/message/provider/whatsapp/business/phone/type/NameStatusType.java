package com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum NameStatusType implements EnumType {
    APPROVED("APPROVED", "APPROVED"),
    AVAILABLE_WITHOUT_REVIEW("AVAILABLE_WITHOUT_REVIEW", "AVAILABLE_WITHOUT_REVIEW"),
    DECLINED("DECLINED", "DECLINED"),
    EXPIRED("EXPIRED", "EXPIRED"),
    NON_EXISTS("NON_EXISTS", "NON_EXISTS"),
    NONE("NONE", "NONE"),
    PENDING_REVIEW("PENDING_REVIEW", "PENDING_REVIEW");

    private final String literal;
    private final String value;

    NameStatusType(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static NameStatusType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(NameStatusType.class, literal);
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
