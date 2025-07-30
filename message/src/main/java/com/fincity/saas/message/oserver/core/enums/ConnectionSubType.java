package com.fincity.saas.message.oserver.core.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ConnectionSubType implements EnumType {
    MONGO("MONGO"),
    OFFICE365("OFFICE365"),
    SENDGRID("SENDGRID"),
    REST_API_OAUTH2("REST_API_OAUTH2"),
    REST_API_BASIC("REST_API_BASIC"),
    REST_API_AUTH("REST_API_AUTH"),
    SMTP("SMTP"),
    EXOTEL("EXOTEL"),
    WHATSAPP("WHATSAPP");

    private final String literal;

    ConnectionSubType(String literal) {
        this.literal = literal;
    }

    public static ConnectionSubType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ConnectionSubType.class, literal);
    }

    public String getProvider() {
        return this.name().toLowerCase();
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return "";
    }
}
