package com.fincity.saas.message.oserver.core.enums;

import java.util.Set;

public enum ConnectionType {
    APP_DATA(ConnectionSubType.MONGO),

    WHATSAPP,

    WHATSAPP_TEMPLATE,

    MAIL(ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),

    REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_AUTH, ConnectionSubType.REST_API_OAUTH2),

    CALL(ConnectionSubType.EXOTEL),

    TEXT_MESSAGE(ConnectionSubType.WHATSAPP);

    private final Set<ConnectionSubType> allowedSubtypes;

    ConnectionType(ConnectionSubType... allowedSubtypes) {
        this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
    }

    public boolean hasConnectionSubType(ConnectionSubType subType) {
        return this.allowedSubtypes.contains(subType);
    }
}
