package com.fincity.saas.message.oserver.core.enums;

public enum ConnectionSubType {
    MONGO,
    OFFICE365,
    SENDGRID,
    WHATSAPP,
    REST_API_OAUTH2,
    REST_API_BASIC,
    REST_API_AUTH,
    SMTP,
    CALL_EXOTEL,
    MESSAGE_WHATSAPP,
    MESSAGE_WHATSAPP_TEMPLATE;

    public String getProvider() {
        String[] parts = this.name().split("_");
        return parts[parts.length - 1].toLowerCase();
    }
}
