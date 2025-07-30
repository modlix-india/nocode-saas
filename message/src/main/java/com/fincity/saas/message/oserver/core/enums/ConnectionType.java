package com.fincity.saas.message.oserver.core.enums;

import java.util.Set;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ConnectionType implements EnumType {
    APP_DATA("APP_DATA", ConnectionSubType.MONGO),

    MAIL("MAIL", ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),

    REST_API(
            "REST_API",
            ConnectionSubType.REST_API_BASIC,
            ConnectionSubType.REST_API_AUTH,
            ConnectionSubType.REST_API_OAUTH2),

    CALL("CALL", ConnectionSubType.EXOTEL),

    TEXT_MESSAGE("TEXT_MESSAGE", ConnectionSubType.WHATSAPP);

    private final String literal;
    private final Set<ConnectionSubType> allowedSubtypes;

    ConnectionType(String literal, ConnectionSubType... allowedSubtypes) {
        this.literal = literal;
        this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
    }

    public boolean hasConnectionSubType(ConnectionSubType subType) {
        return this.allowedSubtypes.contains(subType);
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
