package com.fincity.saas.commons.core.enums;

import java.util.Set;

public enum ConnectionType {
    APP_DATA(ConnectionSubType.MONGO),

    WHATSAPP,

    WHATSAPP_TEMPLATE,

    MAIL(ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),

    TEXT_MESSAGE,

    REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_AUTH, ConnectionSubType.REST_API_OAUTH2),

    NOTIFICATION(ConnectionSubType.NOTIFICATION_DISABLED, ConnectionSubType.NOTIFICATION_EMAIL_SMTP,
            ConnectionSubType.NOTIFICATION_EMAIL_SENDGRID, ConnectionSubType.NOTIFICATION_IN_APP,
            ConnectionSubType.NOTIFICATION_MOBILE_PUSH, ConnectionSubType.NOTIFICATION_WEB_PUSH,
            ConnectionSubType.NOTIFICATION_SMS);;

    private final Set<ConnectionSubType> allowedSubtypes;

    ConnectionType(ConnectionSubType... allowedSubtypes) {
        this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
    }

    public boolean hasConnectionSubType(ConnectionSubType subType) {
        return this.allowedSubtypes.contains(subType);
    }
}
