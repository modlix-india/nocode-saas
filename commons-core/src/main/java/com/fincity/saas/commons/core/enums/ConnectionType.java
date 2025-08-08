package com.fincity.saas.commons.core.enums;

import java.util.Set;

public enum ConnectionType {
    APP_DATA(ConnectionSubType.MONGO),

    MAIL(ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),

    REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_AUTH, ConnectionSubType.REST_API_OAUTH2),

    NOTIFICATION,

    IN_APP,

    MOBILE_PUSH,

    WEB_PUSH,

    SMS(ConnectionSubType.WHATSAPP),

    CALL(ConnectionSubType.EXOTEL);

    private final Set<ConnectionSubType> allowedSubtypes;

    ConnectionType(ConnectionSubType... allowedSubtypes) {
        this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
    }

    public boolean hasConnectionSubType(ConnectionSubType subType) {
        return this.allowedSubtypes.contains(subType);
    }
}
