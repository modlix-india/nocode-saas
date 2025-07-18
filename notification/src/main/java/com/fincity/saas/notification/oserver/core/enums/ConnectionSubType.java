package com.fincity.saas.notification.oserver.core.enums;

public enum ConnectionSubType {
    MONGO,
    OFFICE365,
    SENDGRID,
    WHATSAPP,
    REST_API_OAUTH2,
    REST_API_BASIC,
    REST_API_AUTH,
    SMTP,
    NOTIFICATION_DISABLED,
    NOTIFICATION_EMAIL_SMTP,
    NOTIFICATION_EMAIL_SENDGRID,
    NOTIFICATION_IN_APP,
    NOTIFICATION_MOBILE_PUSH,
    NOTIFICATION_WEB_PUSH,
    NOTIFICATION_SMS;

    public String getProvider() {
        String[] parts = this.name().split("_");
        return parts[parts.length - 1].toLowerCase();
    }
}
