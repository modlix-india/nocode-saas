package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum TemplateRejectedReason implements EnumType {
    ABUSIVE_CONTENT("ABUSIVE_CONTENT", "ABUSIVE_CONTENT"),
    INVALID_FORMAT("INVALID_FORMAT", "INVALID_FORMAT"),
    NONE("NONE", "NONE"),
    PROMOTIONAL("PROMOTIONAL", "PROMOTIONAL"),
    TAG_CONTENT_MISMATCH("TAG_CONTENT_MISMATCH", "TAG_CONTENT_MISMATCH"),
    SCAM("SCAM", "SCAM");

    private final String literal;
    private final String value;

    TemplateRejectedReason(String literal, String value) {
        this.literal = literal;
        this.value = value;
    }

    public static TemplateRejectedReason lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TemplateRejectedReason.class, literal);
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
