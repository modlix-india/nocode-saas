package com.fincity.saas.entity.processor.oserver.core.enums;

public enum ConnectionSubType {
    MONGO,
    MYSQL,
    OFFICE365,
    SENDGRID,
    REST_API_OAUTH2,
    REST_API_BASIC,
    REST_API_AUTH,
    SMTP,
    EXOTEL,
    TELECMI,
    WHATSAPP;

    public String getProvider() {
        return this.name().toLowerCase();
    }
}
